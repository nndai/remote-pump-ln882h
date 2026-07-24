#ifndef CONFIG_H
#define CONFIG_H

#include "generic-ln882h.h"

// ── Version ──
#define FIRMWARE_VERSION   "1.0.0"
#define DEVICE_NAME        "RemotePump"
#ifdef MCU
#define CHIP_MODEL       MCU
#else
#define CHIP_MODEL       "LN882HK"
#endif
// ── LN882H Pin Definitions ──
#define PIN_BL0937_CF      PIN_PB04  // PB04  - BL0937 CF  (power pulse, interrupt)
#define PIN_BL0937_CF1     PIN_PB05  // PB05  - BL0937 CF1 (current/voltage pulse, interrupt)
#define PIN_BL0937_SEL     PIN_PB06  // PB06  - BL0937 SEL (current/voltage select)
#define PIN_NTC_ADC        PIN_PA04  // PA04  - NTC 10k thermistor (ADC-capable pin)
#define PIN_RELAY          PIN_PB03  // PB03  - Relay control
#define PIN_TRIAC_GATE     PIN_PA08  // PA08  - TRIAC gate control
#define PIN_LED            PIN_PA06  // PA06  - Status LED (active HIGH)
#define PIN_BUTTON         PIN_PA07  // PA07  - Push button (active LOW, pull-up)

// ── Network ──
#define WEBSOCKET_PORT      82

// ── File Paths (LittleFS) ──
#define PATH_CONFIG_FILE    "/config.json"
#define PATH_LOG_DIR        "/logs/"
#define PATH_LOG_FILE       "/logs/log.txt"

// ── Default WiFi AP ──
#define DEFAULT_AP_SSID     "REMOTE PUMP"
#define DEFAULT_AP_PASSWORD "12345678"

// ── Default WiFi DEBUG ──
#define DEFAULT_DEBUG_SSID      "DESKTOP-P5540"
#define DEFAULT_DEBUG_PASSWORD  "aaaaaaaa"
#define DEFAULT_DEBUG_IP        {192, 168, 137, 111}
#define DEFAULT_DEBUG_GATEWAY   {192, 168, 137, 1}
#define DEFAULT_DEBUG_NETMASK   {255, 255, 255, 0}

// ── BL0937 Defaults ──

// ── Default Current Thresholds (mA) ──
#define DEFAULT_THRESH_OFF          100     // <100mA  = not running
#define DEFAULT_THRESH_NO_WATER     2000    // <2000mA = no water (dry run)
#define DEFAULT_THRESH_RUNNING      5000    // <5000mA = normal running
#define DEFAULT_THRESH_OVERLOAD     20000   // >20000mA = overload/short

// ── Default Timeouts (ms) ──
#define DEFAULT_NO_WATER_TIMEOUT    7000   // 7s dry run => auto off
#define DEFAULT_OVERLOAD_TIMEOUT    1000    // 1s overload => auto off

// ── Default Pump Mode ──
#define DEFAULT_PUMP_MODE          true

// ── NTC Thermistor (10k + 10k series) ──
#define NTC_SERIES_RESISTOR     10000.0f    // 10k series resistor
#define NTC_NOMINAL_RES         10000.0f    // 10k at 25°C
#define NTC_NOMINAL_TEMP        25.0f       // 25°C
#define NTC_B_VALUE             3950.0f     // Beta coefficient
#define NTC_ADC_MAX             4095.0f     // 12-bit ADC
#define NTC_VREF                3.3f        // Reference voltage

// ── Button ──
#define BUTTON_LONG_PRESS_MS      10000    // 10s hold = enter AP mode
#define BUTTON_CONFIRM_TIMEOUT_MS 3000     // 3s to confirm pump start
#define BUTTON_DEBOUNCE_MS        50

// ── MQTT ──
#define DEFAULT_MQTT_PORT        1883
#define DEFAULT_MQTT_TOPIC       "pump"
#define MQTT_BUFFER_SIZE         5000
#define MQTT_SOCKET_TIMEOUT_SEC  7


// ── FreeRTOS task config ──
#define TASK_NETWORK_STACK       4096
#define TASK_NETWORK_PRIO        3
#define TASK_SENSOR_STACK        512
#define TASK_SENSOR_PRIO         4
#define TASK_BUTTON_STACK        1024
#define TASK_BUTTON_PRIO         1
#define TASK_LED_STACK           512
#define TASK_LED_PRIO            1
#define TASK_NTPCLIENT_STACK     512

#endif // CONFIG_H
