package com.nndai.remotepump.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nndai.remotepump.data.model.PumpState
import com.nndai.remotepump.ui.components.ConnectionBanner
import com.nndai.remotepump.ui.components.PumpControlButton
import com.nndai.remotepump.ui.components.StatusCard
import com.nndai.remotepump.ui.theme.CyanBlue
import com.nndai.remotepump.ui.theme.GreenOk
import com.nndai.remotepump.ui.theme.OrangeWarning
import com.nndai.remotepump.ui.theme.RedError
import com.nndai.remotepump.ui.theme.SecondaryText
import com.nndai.remotepump.util.formatApparentPower
import com.nndai.remotepump.util.formatBytes
import com.nndai.remotepump.util.formatCurrent
import com.nndai.remotepump.util.formatEnergy
import com.nndai.remotepump.util.formatPf
import com.nndai.remotepump.util.formatPower
import com.nndai.remotepump.util.formatRssi
import com.nndai.remotepump.util.formatTemperature
import com.nndai.remotepump.util.formatUptime
import com.nndai.remotepump.util.formatVoltage

private val PurpleDryRun = Color(0xFF9C27B0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: DashboardViewModel = viewModel()
) {
    val status by viewModel.pumpStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isToggling by viewModel.isToggling.collectAsStateWithLifecycle()

    var dismissedFaultState by remember { mutableStateOf<PumpState?>(null) }
    var activeFaultDialogState by remember { mutableStateOf<PumpState?>(null) }

    // Snackbar messages
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Refresh status when screen becomes visible/resumed
    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    // Show fault modal dialog BOTH on entering screen AND on real-time state changes
    val currentPumpState = status?.pumpState ?: PumpState.OFF
    LaunchedEffect(currentPumpState) {
        if (currentPumpState.isLatchedFault) {
            if (dismissedFaultState != currentPumpState) {
                activeFaultDialogState = currentPumpState
            }
        } else {
            dismissedFaultState = null
            activeFaultDialogState = null
        }
    }

    // Modal Dialog cho 3 trạng thái latch (DRY_RUN, CRITICAL_CURRENT, OVERLOAD)
    activeFaultDialogState?.let { faultState ->
        val (faultTitle, faultDesc) = when (faultState) {
            PumpState.DRY_RUN -> Pair(
                "CẢNH BÁO: KHÔ NƯỚC",
                "Phát hiện chạy khô không có nước! Bơm đã tự động ngắt relay để bảo vệ động cơ khỏi cháy hỏng."
            )
            PumpState.CRITICAL_CURRENT -> Pair(
                "CẢNH BÁO: DÒNG ĐIỆN CỰC CAO",
                "Dòng điện vượt quá ngưỡng cực đại cho phép! Bơm đã ngắt khẩn cấp để tránh hư hỏng hệ thống."
            )
            PumpState.OVERLOAD -> Pair(
                "CẢNH BÁO: QUÁ TẢI BƠM",
                "Phát hiện motor bị quá tải! Hệ thống đã ngắt điện để phòng tránh chập cháy."
            )
            else -> Pair("CẢNH BÁO SỰ CỐ", "Phát hiện lỗi nguy hại trên hệ thống bơm.")
        }

        AlertDialog(
            onDismissRequest = {
                dismissedFaultState = currentPumpState
                activeFaultDialogState = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = faultTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RedError
                )
            },
            text = {
                Text(
                    text = faultDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            dismissedFaultState = currentPumpState
                            activeFaultDialogState = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Đóng")
                    }

                    Button(
                        onClick = {
                            dismissedFaultState = currentPumpState
                            activeFaultDialogState = null
                            viewModel.clearPumpFault()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedError),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Xóa lỗi", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Connection banner
        ConnectionBanner(
            state = connectionState,
            onReconnect = { viewModel.reconnect() }
        )

        // 2. Metrics & Status grid (Aligned in 2-column FlowRow)
        AnimatedVisibility(
            visible = status != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val s = status ?: return@AnimatedVisibility
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    // Item 1: Relay Status Card
                    val isRelayOn = s.relay
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isRelayOn) GreenOk.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (isRelayOn) GreenOk else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isRelayOn) Icons.Filled.Power else Icons.Filled.PowerOff,
                                contentDescription = null,
                                tint = if (isRelayOn) GreenOk else SecondaryText,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = if (isRelayOn) "ON" else "OFF",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = if (isRelayOn) GreenOk else SecondaryText
                                )
                                Text(
                                    text = "RELAY STATUS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Item 2: Pump State / Switch State Card
                    val pState = s.pumpState
                    val pStateColor = when (pState) {
                        PumpState.OFF -> SecondaryText
                        PumpState.RUNNING_OK -> GreenOk
                        PumpState.HIGH_CURRENT -> OrangeWarning
                        PumpState.DRY_RUN -> PurpleDryRun
                        PumpState.CRITICAL_CURRENT, PumpState.OVERLOAD -> RedError
                    }
                    val stateTitle = if (s.pumpMode) "PUMP STATE" else "SWITCH STATE"

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        color = pStateColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, pStateColor.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WaterDrop,
                                contentDescription = null,
                                tint = pStateColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = pState.label,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = pStateColor,
                                    maxLines = 1
                                )
                                Text(
                                    text = stateTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Other Metrics Cards
                    StatusCard(
                        icon = Icons.Filled.Bolt,
                        value = s.current.formatCurrent(),
                        label = "Current",
                        iconTint = if (s.pumpState == PumpState.OVERLOAD || s.pumpState == PumpState.CRITICAL_CURRENT) RedError else CyanBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.ElectricalServices,
                        value = s.power.formatPower(),
                        label = "Power",
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.Bolt,
                        value = s.voltage.formatVoltage(),
                        label = "Voltage",
                        iconTint = OrangeWarning,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.FlashOn,
                        value = s.apparent.formatApparentPower(),
                        label = "Apparent Power",
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.Speed,
                        value = s.pf.formatPf(),
                        label = "Power Factor",
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.EnergySavingsLeaf,
                        value = s.energy.formatEnergy(),
                        label = "Energy",
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.DeviceThermostat,
                        value = s.temperature.formatTemperature(),
                        label = "Temperature",
                        iconTint = if (s.temperature > 60) RedError else OrangeWarning,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        icon = Icons.Filled.SignalCellularAlt,
                        value = s.rssi.formatRssi(),
                        label = "RSSI",
                        iconTint = when {
                            s.rssi > -50 -> CyanBlue
                            s.rssi > -70 -> OrangeWarning
                            else -> RedError
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Footer info
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(14.dp)
                        )
                        Text(
                            text = s.uptime.formatUptime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(14.dp)
                        )
                        Text(
                            text = s.heap.formatBytes(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Pump Control Section at Bottom
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (status?.pumpMode == false) "SWITCH CONTROL" else "PUMP CONTROL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                PumpControlButton(
                    isOn = status?.relay == true,
                    enabled = !isToggling,
                    isLoading = isToggling,
                    onClick = { viewModel.togglePump() }
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
