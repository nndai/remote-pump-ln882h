#include "ProvisionManager.h"
#include "BleProvisioning.h"
#include "ConfigManager.h"

#include <Arduino.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <sdk_private.h>
#include "ln_list.h"
#include "wifi_manager.h"

extern ConfigManager configManager;

ProvisionManager::ProvisionManager()
    : _state(State::IDLE)
    , _eventQueue(NULL)
    , _cmdQueue(NULL)
    , _taskHandle(NULL)
    , _connIdx(0xFF)
    , _stateStartTick(0)
    , _wifiEventHandlerId(0)
    , _scanPending(false)
{
}

void ProvisionManager::begin(QueueHandle_t event_queue)
{
    _eventQueue = event_queue;

    // Init BLE stack
    if (!ble_provisioning_init()) {
        LT_EM(PROV, "BLE provisioning init failed");
        return;
    }

    ble_provisioning_set_event_queue(_eventQueue);
    ble_provisioning_start_adv();
    _transitionTo(State::ADV);

    // Create command queue and task
    _cmdQueue = xQueueCreate(4, sizeof(ProvisionMessage));
    xTaskCreate(taskFunc, "provision", 2048, this, 3, &_taskHandle);

    _registerWifiEvent();

    LT_IM(PROV, "ProvisionManager started (ADV mode)");
}

void ProvisionManager::taskFunc(void* param)
{
    ProvisionManager* self = (ProvisionManager*)param;
    self->taskFunction();
}

void ProvisionManager::taskFunction()
{
    ProvisionMessage msg;
    TickType_t lastWake = xTaskGetTickCount();

    while (1) {
        // Check for timeout
        if (_state != State::IDLE && _state != State::ADV) {
            uint32_t elapsed = (xTaskGetTickCount() - _stateStartTick) * portTICK_PERIOD_MS;
            uint32_t timeout = 0;

            switch (_state) {
                case State::CONNECTED:  timeout = PROV_TIMEOUT_CONNECTED; break;
                case State::SCANNING:   timeout = PROV_TIMEOUT_SCAN; break;
                case State::WAIT_CRED:  timeout = PROV_TIMEOUT_CRED; break;
                case State::CONN_WIFI:  timeout = PROV_TIMEOUT_WIFI; break;
                default: break;
            }

            if (timeout > 0 && elapsed >= timeout) {
                LT_IM(PROV, "Timeout in state %d (%dms)", (int)_state, elapsed);
                _onTimeout();
            }
        }

        // Wait for message with 1s timeout (to check timeouts)
        if (xQueueReceive(_cmdQueue, &msg, pdMS_TO_TICKS(1000)) == pdTRUE) {
            processMessage(msg);
        }

        vTaskDelay(pdMS_TO_TICKS(50));
    }
}

void ProvisionManager::processMessage(const ProvisionMessage& msg)
{
    switch (_state) {
        case State::ADV:
            if (msg.event == PROV_EVT_BLE_CONNECTED) {
                _onConnected(msg);
            }
            break;

        case State::CONNECTED:
            if (msg.event == PROV_EVT_BLE_WRITE_CMD) {
                _onWriteCmd(msg);
            } else if (msg.event == PROV_EVT_BLE_DISCONNECTED) {
                _onDisconnected();
            } else if (msg.event == PROV_EVT_TIMEOUT) {
                _onTimeout();
            }
            break;

        case State::SCANNING:
            if (msg.event == PROV_EVT_WIFI_SCAN_DONE) {
                _onScanDone();
            } else if (msg.event == PROV_EVT_BLE_DISCONNECTED) {
                _onDisconnected();
            } else if (msg.event == PROV_EVT_TIMEOUT) {
                _onTimeout();
            }
            break;

        case State::WAIT_CRED:
            if (msg.event == PROV_EVT_BLE_WRITE_CMD) {
                _onWriteCmd(msg);
            } else if (msg.event == PROV_EVT_BLE_DISCONNECTED) {
                _onDisconnected();
            } else if (msg.event == PROV_EVT_TIMEOUT) {
                _onTimeout();
            }
            break;

        case State::CONN_WIFI:
            if (msg.event == PROV_EVT_WIFI_CONNECTED) {
                _onWifiConnected();
            } else if (msg.event == PROV_EVT_WIFI_GOT_IP) {
                _onWifiGotIP();
            } else if (msg.event == PROV_EVT_WIFI_DISCONNECTED) {
                _onWifiDisconnected(0);
            } else if (msg.event == PROV_EVT_BLE_DISCONNECTED) {
                _onDisconnected();
            } else if (msg.event == PROV_EVT_TIMEOUT) {
                _onTimeout();
            }
            break;

        case State::DONE:
        case State::ERROR:
            if (msg.event == PROV_EVT_BLE_DISCONNECTED) {
                _transitionTo(State::ADV);
            }
            break;

        default:
            break;
    }
}

void ProvisionManager::_transitionTo(State newState)
{
    State oldState = _state;
    _state = newState;
    _stateStartTick = xTaskGetTickCount();

    if (oldState != newState) {
        LT_IM(PROV, "State: %d -> %d", (int)oldState, (int)newState);
    }
}

void ProvisionManager::_onConnected(const ProvisionMessage& msg)
{
    _connIdx = msg.conn_idx;
    _transitionTo(State::CONNECTED);
}

void ProvisionManager::_onDisconnected()
{
    _connIdx = 0xFF;
    _scanPending = false;
    _transitionTo(State::ADV);
    ble_provisioning_start_adv();
}

void ProvisionManager::_onWriteCmd(const ProvisionMessage& msg)
{
    // Parse JSON command
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, msg.data, msg.data_len);
    if (err) {
        LT_EM(PROV, "JSON parse error: %s", err.c_str());
        return;
    }

    const char* cmd = doc["c"];

    if (_state == State::CONNECTED || _state == State::WAIT_CRED) {
        if (cmd && strcmp(cmd, "scan_req") == 0) {
            if (_startWifiScan()) {
                _sendStatus("scanning");
                _transitionTo(State::SCANNING);
            } else {
                _sendStatus("scan_error");
            }
            return;
        }
    }

    if (_state == State::WAIT_CRED) {
        if (cmd && strcmp(cmd, "conn_req") == 0) {
            const char* ssid = doc["ssid"];
            const char* pass = doc["pass"];
            if (ssid && strlen(ssid) > 0) {
                if (_startWifiConnect(ssid, pass ? pass : "")) {
                    _sendStatus("connecting");
                    _transitionTo(State::CONN_WIFI);
                } else {
                    _sendStatus("conn_error");
                }
            } else {
                _sendStatus("invalid_ssid");
            }
            return;
        }
    }

    if (cmd && strcmp(cmd, "status_req") == 0) {
        JsonDocument resp;
        resp["s"] = "status";
        resp["code"] = 0;
        resp["state"] = (int)_state;
        String json;
        serializeJson(resp, json);
        ble_provisioning_notify_status(_connIdx, (uint8_t*)json.c_str(), json.length());
    }
}

void ProvisionManager::_onScanDone()
{
    _scanPending = false;
    _sendScanResults();
    _transitionTo(State::WAIT_CRED);
}

void ProvisionManager::_onWifiConnected()
{
    // Wait for GOT_IP
}

void ProvisionManager::_onWifiGotIP()
{
    // Save config
    DeviceConfig& cfg = configManager.get();
    cfg.connMode = ConnMode::STA_MQTT;
    configManager.save(cfg);

    _sendStatus("wifi_connected");
    _transitionTo(State::DONE);

    // Disconnect BLE after short delay
    vTaskDelay(pdMS_TO_TICKS(500));
    ble_provisioning_disconnect();
}

void ProvisionManager::_onWifiDisconnected(int reason)
{
    _sendStatus("wifi_failed");
    _transitionTo(State::WAIT_CRED);
}

void ProvisionManager::_onTimeout()
{
    switch (_state) {
        case State::CONNECTED:
            _sendStatus("idle_timeout");
            break;
        case State::SCANNING:
            _sendStatus("scan_timeout");
            _transitionTo(State::WAIT_CRED);
            return;
        case State::WAIT_CRED:
            _sendStatus("cred_timeout");
            _transitionTo(State::CONNECTED);
            return;
        case State::CONN_WIFI:
            _sendStatus("wifi_timeout");
            _transitionTo(State::WAIT_CRED);
            return;
        default:
            break;
    }
}

void ProvisionManager::_sendStatus(const char* msg)
{
    if (!g_prov_connected) return;

    JsonDocument doc;
    doc["s"] = "status";
    doc["code"] = 0;
    doc["msg"] = msg;
    String json;
    serializeJson(doc, json);
    ble_provisioning_notify_status(_connIdx, (uint8_t*)json.c_str(), json.length());
}

void ProvisionManager::_sendScanResults()
{
    if (!g_prov_connected) return;

    int16_t count = WiFi.scanComplete();
    if (count < 0) {
        _sendStatus("scan_failed");
        return;
    }

    // Get AP list from raw SDK for better data
    ln_list_t* list = NULL;
    uint8_t apCount = 0;
    wifi_manager_get_ap_list(&list, &apCount);

    JsonDocument doc;
    doc["s"] = "scan_result";
    JsonArray nets = doc["nets"].to<JsonArray>();

    int idx = 0;
    ap_info_node_t* pnode;
    LN_LIST_FOR_EACH_ENTRY(pnode, ap_info_node_t, list, list) {
        if (idx >= count) break;
        if (idx >= 20) break;  // limit to top 20
        ap_info_t* ap = &pnode->info;
        JsonObject n = nets.add<JsonObject>();
        n["ssid"] = ap->ssid;
        n["rssi"] = (int32_t)ap->rssi;
        n["auth"] = ap->authmode;
        idx++;
    }

    String json;
    serializeJson(doc, json);
    ble_provisioning_notify_data(_connIdx, (uint8_t*)json.c_str(), json.length());
    WiFi.scanDelete();
}

bool ProvisionManager::_startWifiScan()
{
    if (_scanPending) return false;
    _scanPending = true;
    WiFi.scanDelete();
    WiFi.scanNetworks(true, false, false, 200);
    return true;
}

bool ProvisionManager::_startWifiConnect(const char* ssid, const char* pass)
{
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, pass);
    return true;
}

void ProvisionManager::_registerWifiEvent()
{
    if (_wifiEventHandlerId) return;

    auto handler = [this](EventId event, EventInfo info) {
        ProvisionMessage msg = {};
        msg.conn_idx = _connIdx;

        switch (event) {
            case ARDUINO_EVENT_WIFI_SCAN_DONE:
                msg.event = PROV_EVT_WIFI_SCAN_DONE;
                break;
            case ARDUINO_EVENT_WIFI_STA_CONNECTED:
                msg.event = PROV_EVT_WIFI_CONNECTED;
                break;
            case ARDUINO_EVENT_WIFI_STA_GOT_IP:
                msg.event = PROV_EVT_WIFI_GOT_IP;
                break;
            case ARDUINO_EVENT_WIFI_STA_DISCONNECTED:
                msg.event = PROV_EVT_WIFI_DISCONNECTED;
                msg.data_len = 1;
                msg.data[0] = info.wifi_sta_disconnected.reason;
                break;
            default:
                return;
        }
        if (_cmdQueue) {
            xQueueSend(_cmdQueue, &msg, 0);
        }
    };

    _wifiEventHandlerId = WiFi.onEvent(handler, ARDUINO_EVENT_WIFI_SCAN_DONE);
    WiFi.onEvent(handler, ARDUINO_EVENT_WIFI_STA_CONNECTED);
    WiFi.onEvent(handler, ARDUINO_EVENT_WIFI_STA_GOT_IP);
    WiFi.onEvent(handler, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
}

void ProvisionManager::_unregisterWifiEvent()
{
    if (_wifiEventHandlerId) {
        WiFi.removeEvent(_wifiEventHandlerId);
        _wifiEventHandlerId = 0;
    }
}
