#include "ConfigManager.h"
#include <Config.h>

ConfigManager::ConfigManager() {
    _pref.begin(NAMESPACE, false);
}

bool ConfigManager::load(DeviceConfig& cfg) {
    size_t len = _pref.getBytes(KEY, &cfg, sizeof(DeviceConfig));
    if (len != sizeof(DeviceConfig)) {
        cfg = DeviceConfig();
        return false;
    }
    if (getConnModeString(cfg.connMode) == "UNKNOWN") {
        LT_IM(CFG, "Invalid connection mode, using defaults");
        cfg = DeviceConfig();
        return false;
    }
    return true;
}

bool ConfigManager::save(const DeviceConfig& cfg) {
    size_t written = _pref.putBytes(KEY, &cfg, sizeof(DeviceConfig));
    return written == sizeof(DeviceConfig);
}

bool ConfigManager::reset() {
    _pref.remove(KEY);
    _config = DeviceConfig();
    return true;
}

DeviceConfig& ConfigManager::get() {
    return _config;
}

const char* ConfigManager::getConnModeString(ConnMode mode) {
    switch (mode) {
    case ConnMode::AP_WS:
        return "AP_WS";
    case ConnMode::STA_MQTT:
        return "STA_MQTT";
    case ConnMode::DEBUG_WS:
        return "DEBUG_WS";
    default:
        return "UNKNOWN";
    }
}

void ConfigManager::print() {
    LT_IM(CFG, "── Config ──");
    LT_IM(CFG, "  WiFi: %s", _config.wifiSSID);
    LT_IM(CFG, "  MQTT: %s:%d", _config.mqttServer, _config.mqttPort);
    LT_IM(CFG, "  Topic: %s", _config.mqttTopic);
    LT_IM(CFG, "  Mode: %s", _config.pumpMode ? "PUMP" : "SWITCH");
    LT_IM(CFG, "  DryTimeout: %ds", _config.dryTimeout / 1000);
    LT_IM(CFG, "  OLTimeout: %ds", _config.overloadTimeout / 1000);
    LT_IM(CFG, "  Thresh: off=%dmA dry=%dmA run=%dmA OL=%dmA",
        _config.threshOff, _config.threshNoWater,
        _config.threshRunning, _config.threshOverload);
    LT_IM(CFG, "  Log: logFile=%s level=%d",
        _config.sysLogFileEnabled ? "ON" : "OFF", _config.sysLogFileLevel);
    LT_IM(CFG, "  ConnMode: %s", getConnModeString(_config.connMode));
    if (_config.connMode == ConnMode::DEBUG_WS) {
        LT_IM(CFG, "  DebugSSID: %s", _config.debugSSID);
        LT_IM(CFG, "  DebugIP: %d.%d.%d.%d gw=%d.%d.%d.%d mask=%d.%d.%d.%d",
            _config.debugIp[0], _config.debugIp[1], _config.debugIp[2], _config.debugIp[3],
            _config.debugGateway[0], _config.debugGateway[1], _config.debugGateway[2], _config.debugGateway[3],
            _config.debugNetmask[0], _config.debugNetmask[1], _config.debugNetmask[2], _config.debugNetmask[3]);
    }
}
