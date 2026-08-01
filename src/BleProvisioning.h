#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <FreeRTOS.h>
#include <queue.h>

#include "BleConfig.h"

#ifdef __cplusplus
extern "C" {
#endif

// ── API ──

bool ble_provisioning_init(void);
void ble_provisioning_start_adv(void);
void ble_provisioning_stop_adv(void);
bool ble_provisioning_is_connected(void);
void ble_provisioning_disconnect(void);
bool ble_provisioning_notify_data(uint8_t conn_idx, const uint8_t* data, uint16_t len);
bool ble_provisioning_notify_status(uint8_t conn_idx, const uint8_t* data, uint16_t len);
void ble_provisioning_set_event_queue(QueueHandle_t queue);
bool ble_provisioning_get_conn_idx(uint8_t* conn_idx);

// GATT service handles (read by Provision Manager)
extern uint16_t g_prov_svc_start_handle;
extern uint8_t  g_prov_conn_idx;
extern bool     g_prov_connected;
extern bool     g_prov_data_ccc_enabled;
extern bool     g_prov_status_ccc_enabled;

#ifdef __cplusplus
}
#endif
