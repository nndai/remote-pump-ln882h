#pragma once

#include <stdint.h>

// ── BLE Provisioning Service UUIDs (128-bit, LSB first) ──
// Base: 0000F000-1234-1000-8000-00805F9B34FB
#define PROV_SVC_UUID \
    {0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
     0x00, 0x10, 0x00, 0x00, 0x00, 0xF0, 0x00, 0x00}

// Command characteristic: phone → device (Write)
#define PROV_CHAR_CMD_UUID \
    {0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
     0x00, 0x10, 0x00, 0x00, 0x01, 0xF0, 0x00, 0x00}

// Data characteristic: device → phone (Notify, large payloads)
#define PROV_CHAR_DATA_UUID \
    {0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
     0x00, 0x10, 0x00, 0x00, 0x02, 0xF0, 0x00, 0x00}

// Status characteristic: device → phone (Notify, short status)
#define PROV_CHAR_STATUS_UUID \
    {0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
     0x00, 0x10, 0x00, 0x00, 0x03, 0xF0, 0x00, 0x00}

// ── GATT attribute indices ──
enum ProvAttrIdx {
    PROV_IDX_SVC = 0,
    PROV_IDX_CHAR_CMD,
    PROV_IDX_VAL_CMD,
    PROV_IDX_CHAR_DATA,
    PROV_IDX_VAL_DATA,
    PROV_IDX_CCC_DATA,
    PROV_IDX_CHAR_STATUS,
    PROV_IDX_VAL_STATUS,
    PROV_IDX_CCC_STATUS,
    PROV_IDX_MAX,
};

// ── Max attribute value sizes ──
#define PROV_CMD_MAX_LEN       512
#define PROV_DATA_MAX_LEN      512
#define PROV_STATUS_MAX_LEN    128

// ── BLE Advertising ──
#define PROV_ADV_INT_MIN_MS    40
#define PROV_ADV_INT_MAX_MS    40
#define PROV_DEVICE_NAME       "RemotePump"

// ── BLE Connection parameters (after connect) ──
#define PROV_CONN_INTV_MIN     24    // 30ms
#define PROV_CONN_INTV_MAX     40    // 50ms
#define PROV_CONN_LATENCY      0
#define PROV_CONN_SUP_TIMEOUT  500   // 5000ms

// ── BLE MTU ──
#define PROV_MTU_SIZE          512

// ── Timeouts (ms) ──
#define PROV_TIMEOUT_CONNECTED   30000
#define PROV_TIMEOUT_SCAN        15000
#define PROV_TIMEOUT_CRED        60000
#define PROV_TIMEOUT_WIFI        20000

// ── Provision Event IDs (sent via queue) ──
enum ProvisionEvent : uint8_t {
    PROV_EVT_NONE = 0,
    PROV_EVT_BLE_CONNECTED,
    PROV_EVT_BLE_DISCONNECTED,
    PROV_EVT_BLE_WRITE_CMD,
    PROV_EVT_BLE_CCC_WRITE,
    PROV_EVT_WIFI_SCAN_DONE,
    PROV_EVT_WIFI_CONNECTED,
    PROV_EVT_WIFI_GOT_IP,
    PROV_EVT_WIFI_DISCONNECTED,
    PROV_EVT_TIMEOUT,
};

// ── Queue message ──
struct ProvisionMessage {
    ProvisionEvent event;
    uint8_t conn_idx;
    uint16_t data_len;
    uint8_t data[512];
};
