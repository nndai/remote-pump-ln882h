package com.nndai.remotepump.ui.history

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nndai.remotepump.data.model.DailyEnergyLog
import com.nndai.remotepump.data.model.HourlyEnergyLog
import com.nndai.remotepump.data.model.MonthlyEnergyLog
import com.nndai.remotepump.data.model.ToggleLogEvent
import com.nndai.remotepump.data.repository.LogRepository
import com.nndai.remotepump.ui.theme.CyanBlue
import com.nndai.remotepump.ui.theme.GreenOk
import com.nndai.remotepump.ui.theme.OrangeWarning
import com.nndai.remotepump.ui.theme.RedError
import com.nndai.remotepump.R
import java.util.Locale

import com.nndai.remotepump.data.model.HalfMonthDayEnergyLog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnergyHistoryScreen(
    viewModel: EnergyHistoryViewModel = viewModel()
) {
    val selectedPowerDate by viewModel.selectedPowerDate.collectAsStateWithLifecycle()
    val selectedToggleDate by viewModel.selectedToggleDate.collectAsStateWithLifecycle()
    val selectedHalfMonthDate by viewModel.selectedHalfMonthDate.collectAsStateWithLifecycle()
    val availableDates by viewModel.availableDates.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val dailyEnergyLog by viewModel.dailyEnergyLog.collectAsStateWithLifecycle()
    val halfMonthEnergyLogs by viewModel.halfMonthEnergyLogs.collectAsStateWithLifecycle()
    val monthlyEnergyLogs by viewModel.monthlyEnergyLogs.collectAsStateWithLifecycle()
    val toggleEvents by viewModel.toggleEvents.collectAsStateWithLifecycle()

    var showPowerDatePickerDialog by remember { mutableStateOf(false) }
    var showToggleDatePickerDialog by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var selectedHourLog by remember { mutableStateOf<HourlyEnergyLog?>(null) }
    var selectedHalfMonthDayLog by remember { mutableStateOf<HalfMonthDayEnergyLog?>(null) }

    val todayStr = remember { LogRepository.getTodayDateStr() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.syncData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Top Bar Header (Sync Refresh Button) ──
        TopHeaderBar(
            isSyncing = isSyncing,
            onSyncClick = { viewModel.syncData(force = true) }
        )

        // ── 2. Daily Summary Cards ──
        val totalWh = dailyEnergyLog?.totalWh ?: 0L
        val toggleCount = toggleEvents.size

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Tổng điện năng tiêu thụ ngày
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        val displayValue = if (totalWh >= 1000) {
                            String.format(Locale.US, "%.2f kWh", totalWh / 1000f)
                        } else {
                            "$totalWh Wh"
                        }
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.history_daily_consumption),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card 2: Lịch sử bật/tắt (theo ngày chọn của Toggle Log)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.history_toggle_count_format, toggleCount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.history_toggle_count_today),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── 3. 24-Hour Power Bar Chart with Compact Date Selector ──
        val hourlyList = dailyEnergyLog?.hourlyList ?: (0..23).map { HourlyEnergyLog(it, 0L) }
        DailyHourlyBarChartCard(
            selectedDate = selectedPowerDate,
            todayStr = todayStr,
            hourlyList = hourlyList,
            selectedHourLog = selectedHourLog,
            onHourClick = { selectedHourLog = if (selectedHourLog == it) null else it },
            onSelectDateClick = { showPowerDatePickerDialog = true }
        )

        // ── 4. Daily Power Bar Chart of Selected Month ──
        MonthlyDailyBarChartCard(
            selectedMonth = selectedHalfMonthDate,
            dailyLogs = halfMonthEnergyLogs,
            selectedDayLog = selectedHalfMonthDayLog,
            onDayClick = { selectedHalfMonthDayLog = if (selectedHalfMonthDayLog == it) null else it },
            onSelectMonthClick = { showMonthPickerDialog = true }
        )

        // ── 5. 6-Month Power Bar Chart ──
        MonthlyBarChartCard(monthlyLogs = monthlyEnergyLogs)

        // ── 6. Toggle Logs Event List with Compact Date Selector ──
        ToggleEventsCard(
            selectedDate = selectedToggleDate,
            availableDates = availableDates,
            todayStr = todayStr,
            events = toggleEvents,
            onSelectDateClick = { showToggleDatePickerDialog = true }
        )

        Spacer(modifier = Modifier.height(80.dp))
    }

    // ── Power Date Picker Dialog ──
    if (showPowerDatePickerDialog) {
        CustomVisualCalendarDialog(
            title = stringResource(R.string.history_select_date),
            selectedDateStr = selectedPowerDate,
            availableDates = availableDates,
            onDateSelected = { dateStr ->
                viewModel.selectPowerDate(dateStr)
                showPowerDatePickerDialog = false
            },
            onDismiss = { showPowerDatePickerDialog = false }
        )
    }

    // ── Month Picker Dialog ──
    if (showMonthPickerDialog) {
        CustomVisualMonthPickerDialog(
            title = stringResource(R.string.history_select_month),
            selectedMonthStr = selectedHalfMonthDate,
            availableMonths = availableMonths,
            onMonthSelected = { monthStr ->
                viewModel.selectHalfMonthDate(monthStr)
                showMonthPickerDialog = false
            },
            onDismiss = { showMonthPickerDialog = false }
        )
    }

    // ── Toggle Date Picker Dialog ──
    if (showToggleDatePickerDialog) {
        CustomVisualCalendarDialog(
            title = stringResource(R.string.history_select_date),
            selectedDateStr = selectedToggleDate,
            availableDates = availableDates,
            onDateSelected = { dateStr ->
                viewModel.selectToggleDate(dateStr)
                showToggleDatePickerDialog = false
            },
            onDismiss = { showToggleDatePickerDialog = false }
        )
    }
}

// ── Sub-Components ──

@Composable
private fun TopHeaderBar(
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        IconButton(onClick = onSyncClick) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.history_sync),
                tint = CyanBlue,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (isSyncing) spinAngle else 0f
                }
            )
        }
    }
}

@Composable
private fun DailyHourlyBarChartCard(
    selectedDate: String,
    todayStr: String,
    hourlyList: List<HourlyEnergyLog>,
    selectedHourLog: HourlyEnergyLog?,
    onHourClick: (HourlyEnergyLog) -> Unit,
    onSelectDateClick: () -> Unit
) {
    val canonicalHourlyList = remember(hourlyList) {
        val mapByHour = hourlyList.associateBy { it.hour }
        (0..23).map { h -> mapByHour[h] ?: HourlyEnergyLog(h, 0L) }
    }
    val maxWh = remember(canonicalHourlyList) {
        (canonicalHourlyList.maxOfOrNull { it.energyWh } ?: 0L).coerceAtLeast(1L)
    }
    val displayDate = if (selectedDate == todayStr) selectedDate else selectedDate // Hôm nay

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp, 8.dp, 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header tiêu đề & Nút chọn ngày nhỏ gọn
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = CyanBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_daily_chart_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Nút chọn ngày độc lập cho Power Log
                Surface(
                    onClick = onSelectDateClick,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = CyanBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Tooltip xem chi tiết khi chạm cột
            if (selectedHourLog != null) {
                val h = selectedHourLog.hour
                val timeRangeStr = "${h}h-${(h + 1) % 24}h"
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.history_hour_tooltip, timeRangeStr, selectedHourLog.energyWh),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyanBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Custom 24-Bar Chart theo thứ tự (0..23) với trục Y
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Trục Y (Bên trái)
                Column(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val v100 = if (maxWh >= 1000) String.format(Locale.US, "%.1fk", maxWh / 1000f) else "$maxWh"
                    val v75 = if (maxWh * 0.75f >= 1000) String.format(Locale.US, "%.1fk", (maxWh * 0.75f) / 1000f) else "${(maxWh * 0.75f).toLong()}"
                    val v50 = if (maxWh * 0.50f >= 1000) String.format(Locale.US, "%.1fk", (maxWh * 0.50f) / 1000f) else "${(maxWh * 0.50f).toLong()}"
                    val v25 = if (maxWh * 0.25f >= 1000) String.format(Locale.US, "%.1fk", (maxWh * 0.25f) / 1000f) else "${(maxWh * 0.25f).toLong()}"

                    Text(
                        text = v100,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = v75,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = v50,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = v25,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 24 Cột biểu đồ
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    canonicalHourlyList.forEach { log ->
                        val isSelected = (selectedHourLog?.hour == log.hour)
                        val barHeightRatio = (log.energyWh.toFloat() / maxWh.toFloat()).coerceIn(0.04f, 1f)
                        val barColor = when {
                            isSelected -> OrangeWarning
                            log.energyWh == maxWh && maxWh > 0 -> CyanBlue
                            log.energyWh > 0 -> GreenOk
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onHourClick(log) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height((120 * barHeightRatio).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }

            // X-Axis Hour Labels (0h, 3h, 6h, 9h, 12h, 15h, 18h, 21h, 23h)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0, 3, 6, 9, 12, 15, 18, 21, 23).forEach { hour ->
                    Text(
                        text = "${hour}h",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyBarChartCard(
    monthlyLogs: List<MonthlyEnergyLog>
) {
    val maxWh = remember(monthlyLogs) {
        (monthlyLogs.maxOfOrNull { it.totalWh } ?: 0L).coerceAtLeast(1L)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(2.dp, 20.dp, 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = GreenOk,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_monthly_chart_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.history_unit_kwh),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Trục Y (Bên trái)
                val maxKwh = maxWh / 1000f
                Column(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxSize()
                        .padding(bottom = 18.dp, top = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val k100 = if (maxWh > 0) String.format(Locale.US, "%.1f", maxKwh) else "0"
                    val k75 = if (maxWh > 0) String.format(Locale.US, "%.1f", maxKwh * 0.75f) else "0"
                    val k50 = if (maxWh > 0) String.format(Locale.US, "%.1f", maxKwh * 0.50f) else "0"
                    val k25 = if (maxWh > 0) String.format(Locale.US, "%.1f", maxKwh * 0.25f) else "0"

                    Text(
                        text = k100,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = k75,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = k50,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = k25,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Các cột tháng
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    monthlyLogs.forEach { mLog ->
                        val ratio = (mLog.totalWh.toFloat() / maxWh.toFloat()).coerceIn(0.04f, 1f)
                        val kwhVal = mLog.totalWh / 1000f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (mLog.totalWh > 0) String.format(Locale.US, "%.1f", kwhVal) else "0",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height((120 * ratio).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (mLog.totalWh > 0) CyanBlue else MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = mLog.yearMonthStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleEventsCard(
    selectedDate: String,
    availableDates: List<String>,
    todayStr: String,
    events: List<ToggleLogEvent>,
    onSelectDateClick: () -> Unit
) {
    val displayDate = if (selectedDate == todayStr) selectedDate else selectedDate // Hôm nay

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header với chọn ngày độc lập cho Toggle Logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Power,
                        contentDescription = null,
                        tint = OrangeWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_toggle_log_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Date Selector cho Toggle Log
                Surface(
                    onClick = onSelectDateClick,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = OrangeWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_no_toggle_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    events.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.timeStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = item.source.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // State Badge ON / OFF
                            val badgeColor = if (item.state) GreenOk else RedError
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = badgeColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = if (item.state) "ON" else "OFF",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomVisualCalendarDialog(
    title: String,
    selectedDateStr: String,
    availableDates: List<String>,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val todayStr = remember { LogRepository.getTodayDateStr() }

    val initialDateParts = remember(selectedDateStr) {
        val parts = selectedDateStr.split("-")
        if (parts.size >= 3) {
            Triple(
                parts[0].toIntOrNull() ?: 1,
                parts[1].toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1),
                parts[2].toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            )
        } else {
            val cal = Calendar.getInstance()
            Triple(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
    }

    var displayYear by remember { mutableStateOf(initialDateParts.third) }
    var displayMonth by remember { mutableStateOf(initialDateParts.second) }
    var tempSelectedDateStr by remember { mutableStateOf(selectedDateStr) }

    val cal = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val offset = (firstDayOfWeek + 5) % 7

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    modifier = Modifier.clickable {
                        val todayParts = todayStr.split("-")
                        if (todayParts.size >= 3) {
                            displayMonth = todayParts[1].toIntOrNull() ?: displayMonth
                            displayYear = todayParts[2].toIntOrNull() ?: displayYear
                        }
                        tempSelectedDateStr = todayStr
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = CyanBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CyanBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = stringResource(R.string.history_today),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (displayMonth == 1) {
                                displayMonth = 12
                                displayYear -= 1
                            } else {
                                displayMonth -= 1
                            }
                        }
                    ) {
                        Text("<", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanBlue)
                    }

                    Text(
                        text = String.format(Locale.US, stringResource(R.string.history_month_header), displayMonth, displayYear),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = {
                            if (displayMonth == 12) {
                                displayMonth = 1
                                displayYear += 1
                            } else {
                                displayMonth += 1
                            }
                        }
                    ) {
                        Text(">", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanBlue)
                    }
                }

                val weekDays = LocalContext.current.resources.getStringArray(R.array.history_week_days)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDays.forEachIndexed { idx, dayName ->
                        Text(
                            text = dayName,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (idx == 6) RedError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val totalCells = offset + daysInMonth
                val totalRows = (totalCells + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until totalRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (c in 0..6) {
                                val cellIdx = r * 7 + c
                                val dayNum = cellIdx - offset + 1

                                if (cellIdx < offset || dayNum > daysInMonth) {
                                    Spacer(modifier = Modifier.weight(1f).height(36.dp))
                                } else {
                                    val dateStr = String.format(Locale.US, "%02d-%02d-%04d", dayNum, displayMonth, displayYear)
                                    val isSelected = (dateStr == tempSelectedDateStr)
                                    val isToday = (dateStr == todayStr)
                                    val hasData = availableDates.contains(dateStr)

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                tempSelectedDateStr = dateStr
                                            },
                                        shape = CircleShape,
                                        color = when {
                                            isSelected -> CyanBlue
                                            else -> Color.Transparent
                                        },
                                        border = when {
                                            !isSelected && isToday -> BorderStroke(1.5.dp, CyanBlue)
                                            else -> null
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> Color.White
                                                    c == 6 -> RedError
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (hasData && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(GreenOk)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDateSelected(tempSelectedDateStr)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.history_choose), fontWeight = FontWeight.Bold, color = CyanBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.history_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun MonthlyDailyBarChartCard(
    selectedMonth: String,
    dailyLogs: List<HalfMonthDayEnergyLog>,
    selectedDayLog: HalfMonthDayEnergyLog?,
    onDayClick: (HalfMonthDayEnergyLog) -> Unit,
    onSelectMonthClick: () -> Unit
) {
    val maxWh = remember(dailyLogs) {
        (dailyLogs.maxOfOrNull { it.totalWh } ?: 0L).coerceAtLeast(1L)
    }
    val lastDay = dailyLogs.lastOrNull()?.day ?: 31

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp, 8.dp, 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header tiêu đề & Nút chọn tháng
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = CyanBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_monthly_daily_chart_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Nút chọn tháng
                Surface(
                    onClick = onSelectMonthClick,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = CyanBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_month_label, selectedMonth),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Tooltip xem chi tiết khi chạm cột
            if (selectedDayLog != null) {
                val displayWh = if (selectedDayLog.totalWh >= 1000) {
                    String.format(Locale.US, "%.2f kWh", selectedDayLog.totalWh / 1000f)
                } else {
                    "${selectedDayLog.totalWh} Wh"
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.history_day_tooltip, selectedDayLog.day, selectedMonth, displayWh),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyanBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Custom Bar Chart với Trục Y
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Trục Y (Bên trái)
                Column(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val v100 = if (maxWh >= 1000) String.format(Locale.US, "%.1f", maxWh / 1000f) else "$maxWh"
                    val v75 = if (maxWh * 0.75f >= 1000) String.format(Locale.US, "%.1f", (maxWh * 0.75f) / 1000f) else "${(maxWh * 0.75f).toLong()}"
                    val v50 = if (maxWh * 0.50f >= 1000) String.format(Locale.US, "%.1f", (maxWh * 0.50f) / 1000f) else "${(maxWh * 0.50f).toLong()}"
                    val v25 = if (maxWh * 0.25f >= 1000) String.format(Locale.US, "%.1f", (maxWh * 0.25f) / 1000f) else "${(maxWh * 0.25f).toLong()}"

                    Text(
                        text = v100,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = v75,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = v50,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = v25,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Các cột biểu đồ các ngày trong tháng
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dailyLogs.forEach { log ->
                        val isSelected = (selectedDayLog?.day == log.day)
                        val barHeightRatio = (log.totalWh.toFloat() / maxWh.toFloat()).coerceIn(0.04f, 1f)
                        val barColor = when {
                            isSelected -> OrangeWarning
                            log.totalWh == maxWh && maxWh > 0 -> CyanBlue
                            log.totalWh > 0 -> GreenOk
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onDayClick(log) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height((120 * barHeightRatio).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }

            // X-Axis Day Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labelDays = listOf(1, 5, 10, 15, 20, 25, lastDay)

                labelDays.forEach { day ->
                    Text(
                        text = "$day",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomVisualMonthPickerDialog(
    title: String,
    selectedMonthStr: String,
    availableMonths: List<String>,
    onMonthSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialParts = remember(selectedMonthStr) {
        val parts = selectedMonthStr.split("/")
        if (parts.size >= 2) {
            Pair(parts[0].toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1), parts[1].toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR))
        } else {
            val cal = Calendar.getInstance()
            Pair(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
    }

    var selectedYear by remember { mutableStateOf(initialParts.second) }
    var selectedMonth by remember { mutableStateOf(initialParts.first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Year Switcher ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear -= 1 }) {
                        Text("<", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanBlue)
                    }
                    Text(
                        text = stringResource(R.string.history_year_label, selectedYear),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { selectedYear += 1 }) {
                        Text(">", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanBlue)
                    }
                }

                // ── 12 Months Grid ──
                val monthsList = LocalContext.current.resources.getStringArray(R.array.history_month_names)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0..2) {
                                val monthIdx = row * 3 + col + 1
                                val isSelected = (monthIdx == selectedMonth)
                                val monthFormatted = String.format(Locale.US, "%02d/%04d", monthIdx, selectedYear)
                                val hasData = availableMonths.contains(monthFormatted)

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedMonth = monthIdx
                                        },
                                    color = if (isSelected) CyanBlue else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    border = if (!isSelected && hasData) BorderStroke(1.dp, GreenOk.copy(alpha = 0.6f)) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = monthsList[monthIdx - 1],
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val formattedMonth = String.format(Locale.US, "%02d/%04d", selectedMonth, selectedYear)
                    onMonthSelected(formattedMonth)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.history_choose), fontWeight = FontWeight.Bold, color = CyanBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.history_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
