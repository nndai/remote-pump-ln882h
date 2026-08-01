#include "BleProvisioning.h"

#include <Arduino.h>
#include <sdk_private.h>

// BLE SDK headers (C headers, must be wrapped in extern "C")
extern "C" {
#include "rwip_config.h"
#include "ln_ble_gap.h"
#include "ln_ble_gatt.h"
#include "ln_ble_advertising.h"
#include "ln_ble_connection_manager.h"
#include "ln_ble_event_manager.h"
#include "ln_ble_smp.h"
#include "ln_ble_app_kv.h"
#include "ln_ble_app_defines.h"
#include "ln_ble_app_default_cfg.h"
#include "ln_ble_rw_app_task.h"
#include "ln_ble_device_manager.h"
#include "att.h"
#include "gap.h"
#include "gapm_task.h"
#include "ln_misc.h"
}

// ── Exported state ──
uint16_t g_prov_svc_start_handle = LN_ATT_INVALID_HANDLE;
uint8_t  g_prov_conn_idx = APP_CONN_INVALID_IDX;
bool     g_prov_connected = false;
bool     g_prov_data_ccc_enabled = false;
bool     g_prov_status_ccc_enabled = false;

// ── Internal state ──
static QueueHandle_t s_event_queue = NULL;
static bool s_adv_data_set = false;
static uint8_t s_adv_data_buf[ADV_DATA_LEGACY_MAX];
static uint8_t s_adv_rsp_buf[ADV_DATA_LEGACY_MAX];
static uint8_t s_notify_buf[PROV_DATA_MAX_LEN];

// ── Forward declarations ──
static void on_ble_connected(void* arg);
static void on_ble_disconnected(void* arg);
static void on_ble_write_req(void* arg);
static void on_ble_read_req(void* arg);

// ── Attribute table ──
// The provisioning service has these attributes:
//   [0] Primary Service Declaration
//   [1] Characteristic Declaration (Command)
//   [2] Command Value
//   [3] Characteristic Declaration (Data)
//   [4] Data Value
//   [5] Data CCCD
//   [6] Characteristic Declaration (Status)
//   [7] Status Value
//   [8] Status CCCD

static const ln_gatt_att_desc_t s_prov_atts[PROV_IDX_MAX] = {

    [PROV_IDX_SVC] = {
        .uuid    = {0x00, 0x28},
        .perm    = PERM_MASK_RD,
        .max_len = 0,
        .ext_perm = (0 << PERM_POS_UUID_LEN),
    },

    [PROV_IDX_CHAR_CMD] = {
        .uuid    = {0x03, 0x28},
        .perm    = PERM_MASK_RD,
        .max_len = 0,
        .ext_perm = 0,
    },

    [PROV_IDX_VAL_CMD] = {
        .uuid    = PROV_CHAR_CMD_UUID,
        .perm    = PERM_MASK_WRITE_REQ | PERM_MASK_WRITE_COMMAND | PERM_MASK_RD,
        .max_len = PROV_CMD_MAX_LEN,
        .ext_perm = (2 << PERM_POS_UUID_LEN) | (1 << PERM_POS_RI),
    },

    [PROV_IDX_CHAR_DATA] = {
        .uuid    = {0x03, 0x28},
        .perm    = PERM_MASK_RD,
        .max_len = 0,
        .ext_perm = 0,
    },

    [PROV_IDX_VAL_DATA] = {
        .uuid    = PROV_CHAR_DATA_UUID,
        .perm    = PERM_MASK_NTF | PERM_MASK_RD,
        .max_len = PROV_DATA_MAX_LEN,
        .ext_perm = (2 << PERM_POS_UUID_LEN) | (1 << PERM_POS_RI),
    },

    [PROV_IDX_CCC_DATA] = {
        .uuid    = {0x02, 0x29},
        .perm    = PERM_MASK_WRITE_REQ | PERM_MASK_RD,
        .max_len = 0,
        .ext_perm = 0,
    },

    [PROV_IDX_CHAR_STATUS] = {
        .uuid    = {0x03, 0x28},
        .perm    = PERM_MASK_RD,
        .max_len = 0,
        .ext_perm = 0,
    },

    [PROV_IDX_VAL_STATUS] = {
        .uuid    = PROV_CHAR_STATUS_UUID,
        .perm    = PERM_MASK_NTF | PERM_MASK_RD,
        .max_len = PROV_STATUS_MAX_LEN,
        .ext_perm = (2 << PERM_POS_UUID_LEN) | (1 << PERM_POS_RI),
    },

    [PROV_IDX_CCC_STATUS] = {
        .uuid    = {0x02, 0x29},
        .perm    = PERM_MASK_WRITE_REQ | PERM_MASK_RD,
        .max_len = 0,
        .ext_perm = 0,
    },
};

static const ln_gatt_svc_desc_t s_prov_svc = {
    .start_hdl = LN_ATT_INVALID_HANDLE,
    .perm      = (2 << 5),  // UUID length = 128-bit
    .nb_att    = PROV_IDX_MAX,
    .uuid      = PROV_SVC_UUID,
    .atts      = s_prov_atts,
};

// ── Push event to queue (called from BLE callback context) ──
static void push_event(ProvisionEvent evt, uint8_t conn_idx, const uint8_t* data, uint16_t len)
{
    if (!s_event_queue) return;

    ProvisionMessage msg;
    msg.event = evt;
    msg.conn_idx = conn_idx;
    msg.data_len = (len > sizeof(msg.data)) ? sizeof(msg.data) : len;
    if (data && msg.data_len > 0) {
        memcpy(msg.data, data, msg.data_len);
    } else {
        msg.data_len = 0;
    }
    xQueueSend(s_event_queue, &msg, 0);
}

// ── BLE event callbacks ──

static void on_ble_connected(void* arg)
{
    ble_evt_connected_t* evt = (ble_evt_connected_t*)arg;
    g_prov_conn_idx = evt->conn_idx;
    g_prov_connected = true;

    LT_IM(BLE, "Connected conn_idx=%d interval=%d latency=%d sup_to=%d",
          evt->conn_idx, evt->con_interval, evt->con_latency, evt->sup_to);

    // Negotiate MTU
    ln_gatt_exc_mtu(evt->conn_idx);

    // Update connection parameters for low latency
    ln_ble_conn_param_t conn_param;
    conn_param.intv_min = PROV_CONN_INTV_MIN;
    conn_param.intv_max = PROV_CONN_INTV_MAX;
    conn_param.latency  = PROV_CONN_LATENCY;
    conn_param.time_out = PROV_CONN_SUP_TIMEOUT;
    ln_ble_conn_param_update(evt->conn_idx, &conn_param);

    // Set data packet size
    ln_gap_set_le_pkt_size_t pkt_size;
    pkt_size.tx_octets = 251;
    pkt_size.tx_time   = 2120;
    ln_gap_set_le_pkt_size(evt->conn_idx, &pkt_size);

    push_event(PROV_EVT_BLE_CONNECTED, evt->conn_idx, NULL, 0);
}

static void on_ble_disconnected(void* arg)
{
    ble_evt_disconnected_t* evt = (ble_evt_disconnected_t*)arg;
    LT_IM(BLE, "Disconnected conn_idx=%d reason=%d", evt->conn_idx, evt->reason);

    g_prov_connected = false;
    g_prov_data_ccc_enabled = false;
    g_prov_status_ccc_enabled = false;

    push_event(PROV_EVT_BLE_DISCONNECTED, evt->conn_idx, NULL, 0);
}

static void on_ble_write_req(void* arg)
{
    ble_evt_gatt_write_req_t* evt = (ble_evt_gatt_write_req_t*)arg;

    // Check if write is to CCCD
    if (g_prov_svc_start_handle != LN_ATT_INVALID_HANDLE) {
        uint16_t data_ccc_hdl = g_prov_svc_start_handle + PROV_IDX_CCC_DATA;
        uint16_t status_ccc_hdl = g_prov_svc_start_handle + PROV_IDX_CCC_STATUS;

        if (evt->handle == data_ccc_hdl && evt->length >= 2) {
            g_prov_data_ccc_enabled = (evt->value[0] & 0x01);
            LT_IM(BLE, "Data CCC enabled=%d", g_prov_data_ccc_enabled);
            ln_gatt_write_cfm_t cfm;
            cfm.handle = evt->handle;
            cfm.status = 0;
            ln_gatt_write_req_cfm(evt->conidx, &cfm);
            return;
        }
        if (evt->handle == status_ccc_hdl && evt->length >= 2) {
            g_prov_status_ccc_enabled = (evt->value[0] & 0x01);
            LT_IM(BLE, "Status CCC enabled=%d", g_prov_status_ccc_enabled);
            ln_gatt_write_cfm_t cfm;
            cfm.handle = evt->handle;
            cfm.status = 0;
            ln_gatt_write_req_cfm(evt->conidx, &cfm);
            return;
        }
    }

    // Forward command write to provision manager
    push_event(PROV_EVT_BLE_WRITE_CMD, evt->conidx, evt->value, evt->length);
    ln_gatt_write_cfm_t cfm;
    cfm.handle = evt->handle;
    cfm.status = 0;
    ln_gatt_write_req_cfm(evt->conidx, &cfm);
}

static void on_ble_read_req(void* arg)
{
    // No custom read handling needed; the stack returns the stored attribute value.
    ble_evt_gatt_read_req_t* evt = (ble_evt_gatt_read_req_t*)arg;
    ln_gatt_read_cfm_t cfm;
    cfm.handle = evt->handle;
    cfm.status = 0;
    cfm.length = 0;
    ln_gatt_read_req_cfm(evt->conidx, &cfm);
}

// ── BLE stack init ──
static void ble_stack_init(void)
{
    // 1. Init BLE KV storage (MAC, name, IRK, etc.)
    ln_kv_ble_app_init();

    // 2. Get MAC address (generate random if default)
    ln_bd_addr_t bt_addr = {0};
    ln_bd_addr_t* kv_addr = ln_kv_ble_pub_addr_get();
    memcpy(&bt_addr, kv_addr, sizeof(ln_bd_addr_t));

    ln_bd_addr_t default_addr = BLE_DEFAULT_PUBLIC_ADDR;
    if (memcmp(kv_addr->addr, default_addr.addr, LN_BD_ADDR_LEN) == 0) {
        ln_generate_random_mac(bt_addr.addr);
        bt_addr.addr[5] |= 0xC0;
        ln_kv_ble_addr_store(bt_addr);
    }

    // 3. Controller init
    extern void rw_init(uint8_t mac[6]);
    rw_init(bt_addr.addr);

    // 4. Host init
    ln_gap_app_init();
    ln_gatt_app_init();

    // 5. App component init
    ln_ble_conn_mgr_init();
    ln_ble_evt_mgr_init();
    ln_ble_smp_init();
    ln_ble_adv_mgr_init();

    // 6. Register default callbacks
    ln_ble_evt_mgr_reg_evt(BLE_EVT_ID_CONNECTED,    on_ble_connected);
    ln_ble_evt_mgr_reg_evt(BLE_EVT_ID_DISCONNECTED, on_ble_disconnected);
    ln_ble_evt_mgr_reg_evt(BLE_EVT_ID_GATT_WRITE_REQ, on_ble_write_req);
    ln_ble_evt_mgr_reg_evt(BLE_EVT_ID_GATT_READ_REQ,  on_ble_read_req);

    // 7. BLE app task (SDK internal)
    ln_rw_app_task_init();

    // 8. Stack start
    ln_gap_reset();

    uint8_t* mac = bt_addr.addr;
    LT_IM(BLE, "Stack initialized");
    LT_IM(BLE, "  MAC: %02X:%02X:%02X:%02X:%02X:%02X",
          mac[5], mac[4], mac[3], mac[2], mac[1], mac[0]);
}

// ── Add GATT service ──
static bool add_provisioning_service(void)
{
    ln_gatt_add_svc_req_t req;
    req.svc_desc = s_prov_svc;
    ln_gatt_add_svc(&req);

    g_prov_svc_start_handle = req.svc_desc.start_hdl;

    if (g_prov_svc_start_handle == LN_ATT_INVALID_HANDLE) {
        LT_EM(BLE, "Failed to add provisioning service");
        return false;
    }

    LT_IM(BLE, "Provisioning service added start_hdl=%d", g_prov_svc_start_handle);
    return true;
}

// ── Build legacy advertising data with device name ──
static void build_adv_data(void)
{
    uint8_t len = 0;
    const char* name = PROV_DEVICE_NAME;
    uint8_t name_len = strlen(name);
    if (name_len > (ADV_DATA_LEGACY_MAX - 2)) {
        name_len = ADV_DATA_LEGACY_MAX - 2;
    }

    s_adv_data_buf[len++] = name_len + 1;
    s_adv_data_buf[len++] = GAP_AD_TYPE_COMPLETE_NAME;
    memcpy(&s_adv_data_buf[len], name, name_len);
    len += name_len;

    ln_adv_data_t adv = {.length = len, .data = s_adv_data_buf};
    ln_ble_adv_data_set(&adv);
}

// ── Public API ──

bool ble_provisioning_init(void)
{
    ble_stack_init();
    if (!add_provisioning_service()) return false;
    build_adv_data();
    return true;
}

void ble_provisioning_start_adv(void)
{
    if (s_adv_data_set) return;

    adv_param_t* param = &le_adv_mgr_info_get()->adv_param;
    param->own_addr_type = 1;      // random
    param->adv_type = GAPM_ADV_TYPE_LEGACY;
    param->adv_prop = GAPM_ADV_PROP_UNDIR_CONN_MASK;
    param->adv_intv_min = PROV_ADV_INT_MIN_MS * 1000 / 625;  // convert ms to unit of 0.625ms
    param->adv_intv_max = PROV_ADV_INT_MAX_MS * 1000 / 625;

    ln_ble_adv_actv_creat(param);
    build_adv_data();
    ln_ble_adv_start();
    s_adv_data_set = true;

    LT_IM(BLE, "Advertising started (name=%s)", PROV_DEVICE_NAME);
}

void ble_provisioning_stop_adv(void)
{
    if (!s_adv_data_set) return;
    ln_ble_adv_stop();
    s_adv_data_set = false;
    LT_IM(BLE, "Advertising stopped");
}

bool ble_provisioning_is_connected(void)
{
    return g_prov_connected;
}

void ble_provisioning_disconnect(void)
{
    if (!g_prov_connected) return;
    ln_ble_disc_req(g_prov_conn_idx);
}

bool ble_provisioning_notify_data(uint8_t conn_idx, const uint8_t* data, uint16_t len)
{
    if (!g_prov_data_ccc_enabled) return false;
    if (g_prov_svc_start_handle == LN_ATT_INVALID_HANDLE) return false;

    uint16_t val_handle = g_prov_svc_start_handle + PROV_IDX_VAL_DATA;
    uint16_t send_len = (len > PROV_DATA_MAX_LEN) ? PROV_DATA_MAX_LEN : len;

    ln_gatt_send_evt_cmd_t cmd;
    cmd.handle = val_handle;
    cmd.length = send_len;
    cmd.value  = (uint8_t*)data;
    ln_gatt_send_ntf(conn_idx, &cmd);
    return true;
}

bool ble_provisioning_notify_status(uint8_t conn_idx, const uint8_t* data, uint16_t len)
{
    if (!g_prov_status_ccc_enabled) return false;
    if (g_prov_svc_start_handle == LN_ATT_INVALID_HANDLE) return false;

    uint16_t val_handle = g_prov_svc_start_handle + PROV_IDX_VAL_STATUS;
    uint16_t send_len = (len > PROV_STATUS_MAX_LEN) ? PROV_STATUS_MAX_LEN : len;

    ln_gatt_send_evt_cmd_t cmd;
    cmd.handle = val_handle;
    cmd.length = send_len;
    cmd.value  = (uint8_t*)data;
    ln_gatt_send_ntf(conn_idx, &cmd);
    return true;
}

void ble_provisioning_set_event_queue(QueueHandle_t queue)
{
    s_event_queue = queue;
}

bool ble_provisioning_get_conn_idx(uint8_t* conn_idx)
{
    if (!g_prov_connected) return false;
    *conn_idx = g_prov_conn_idx;
    return true;
}
