#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <FreeRTOS.h>
#include <queue.h>
#include <task.h>

#include "BleConfig.h"

class ProvisionManager {
public:
    ProvisionManager();

    void begin(QueueHandle_t event_queue);
    void processMessage(const ProvisionMessage& msg);
    void taskFunction();

    QueueHandle_t getCommandQueue() const { return _cmdQueue; }

private:
    enum class State : uint8_t {
        IDLE,
        ADV,
        CONNECTED,
        SCANNING,
        WAIT_CRED,
        CONN_WIFI,
        DONE,
        ERROR,
    };

    State _state;
    QueueHandle_t _eventQueue;
    QueueHandle_t _cmdQueue;
    TaskHandle_t _taskHandle;
    uint8_t _connIdx;
    TickType_t _stateStartTick;

    // WiFi event handler ID
    int _wifiEventHandlerId;
    bool _scanPending;

    void _transitionTo(State newState);
    void _onConnected(const ProvisionMessage& msg);
    void _onDisconnected();
    void _onWriteCmd(const ProvisionMessage& msg);
    void _onScanDone();
    void _onWifiConnected();
    void _onWifiGotIP();
    void _onWifiDisconnected(int reason);
    void _onTimeout();

    void _sendStatus(const char* msg);
    void _sendScanResults();
    bool _startWifiScan();
    bool _startWifiConnect(const char* ssid, const char* pass);
    void _registerWifiEvent();
    void _unregisterWifiEvent();

    static void taskFunc(void* param);
};
