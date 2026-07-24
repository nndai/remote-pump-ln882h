package com.nndai.remotepump.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.nndai.remotepump.data.model.DailyEnergyLog
import com.nndai.remotepump.data.model.HourlyEnergyLog
import com.nndai.remotepump.data.model.MonthlyEnergyLog
import com.nndai.remotepump.data.model.ToggleLogEvent
import com.nndai.remotepump.data.model.ToggleSource
import com.nndai.remotepump.data.remote.PumpCommandDataSource
import com.nndai.remotepump.data.remote.PumpCommandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Session theo dõi yêu cầu đọc file chủ động của thiết bị hiện tại.
 */
private data class FileReadSession(
    val reqId: String,
    val expectedOffset: Long,
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * Repository quản lý dữ liệu Lịch sử Năng Lượng (Power Logs) và Lịch sử Bật/Tắt (Toggle Logs).
 * Hỗ trợ xác thực reqId và offset để tránh xung đột khi nhiều thiết bị dùng chung 1 máy chủ MQTT.
 */
class LogRepository(
    private val context: Context,
    private val remote: PumpCommandDataSource,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()

    private val _dailyPowerLogs = MutableStateFlow<Map<String, DailyEnergyLog>>(emptyMap())
    val dailyPowerLogs: StateFlow<Map<String, DailyEnergyLog>> = _dailyPowerLogs.asStateFlow()

    private val _dailyToggleLogs = MutableStateFlow<Map<String, List<ToggleLogEvent>>>(emptyMap())
    val dailyToggleLogs: StateFlow<Map<String, List<ToggleLogEvent>>> = _dailyToggleLogs.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val pendingFileBuffers = mutableMapOf<String, StringBuilder>()

    // Theo dõi reqId chủ động của máy này
    private var activeToggleListDirReqId: String? = null
    private var activePowerListDirReqId: String? = null
    private val activeReadFileSessions = mutableMapOf<String, FileReadSession>()

    init {
        loadCachedData()
        scope.launch {
            remote.events.collect { event ->
                when (event) {
                    is PumpCommandEvent.ListDirResult -> handleListDirResult(event)
                    is PumpCommandEvent.ReadFileResult -> handleReadFileResult(event)
                    is PumpCommandEvent.StatusUpdate -> handleStatusUpdate(event)
                    else -> {}
                }
            }
        }
    }

    /**
     * Nạp dữ liệu đã lưu từ SharedPreferences (KEY_RAW_CONTENT) lên bộ nhớ khi mở ứng dụng.
     */
    private fun loadCachedData() {
        val powerMap = mutableMapOf<String, DailyEnergyLog>()
        val toggleMap = mutableMapOf<String, List<ToggleLogEvent>>()
        val datesSet = mutableSetOf<String>()

        // 1. Load từ JSON cache trước (có thể chứa dữ liệu realtime đã gộp)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX_POWER) && value is String) {
                val dateStr = key.removePrefix(PREFIX_POWER)
                val dailyLog = parseCachedPowerJson(dateStr, value)
                if (dailyLog != null) {
                    powerMap[dateStr] = dailyLog
                    datesSet.add(dateStr)
                }
            } else if (key.startsWith(PREFIX_TOGGLE) && value is String) {
                val dateStr = key.removePrefix(PREFIX_TOGGLE)
                val events = parseCachedToggleJson(value)
                toggleMap[dateStr] = events
                datesSet.add(dateStr)
            }
        }

        // 2. Chỉ load từ RAW CONTENT nếu chưa có trong JSON (tránh ghi đè dữ liệu realtime đã lưu)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_RAW_CONTENT) && value is String && value.isNotEmpty()) {
                val fullPath = key.removePrefix(KEY_RAW_CONTENT)
                val filename = fullPath.substringAfterLast("/")
                val dateStr = filename.removeSuffix(".log")
                if (dateStr.isNotBlank()) {
                    if (fullPath.contains("/power/")) {
                        if (!powerMap.containsKey(dateStr)) {
                            val dailyLog = parsePowerLogData(dateStr, value)
                            powerMap[dateStr] = dailyLog
                            datesSet.add(dateStr)
                        }
                    } else if (fullPath.contains("/toggle/")) {
                        if (!toggleMap.containsKey(dateStr)) {
                            val events = parseToggleLogData(value)
                            toggleMap[dateStr] = events
                            datesSet.add(dateStr)
                        }
                    }
                }
            }
        }

        _dailyPowerLogs.value = powerMap
        _dailyToggleLogs.value = toggleMap
        _availableDates.value = sortDates(datesSet.toList())
    }

    /**
     * Bắt đầu đồng bộ danh sách file từ thiết bị (/logs/power/ và /logs/toggle/).
     * Kèm reqId độc quyền của điện thoại hiện tại.
     */
    fun syncLogs(force: Boolean = false) {
        scope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true

            // Đặt timeout 30s để tự động tắt trạng thái quay (isSyncing) nếu không nhận được phản hồi
            scope.launch {
                kotlinx.coroutines.delay(30_000)
                if (_isSyncing.value) {
                    Log.w(TAG, "Sync timeout! Resetting isSyncing to false.")
                    _isSyncing.value = false
                    activePowerListDirReqId = null
                    activeToggleListDirReqId = null
                }
            }

            if (force) {
                Log.d(TAG, "syncLogs() - FORCE SYNC for both power and toggle")
                activeToggleListDirReqId = remote.listDir("/logs/toggle/")
                activePowerListDirReqId = remote.listDir("/logs/power/")
                return@launch
            }

            val lastCheckDateStr = prefs.getString(KEY_LAST_POWER_CHECK_DATE, "") ?: ""
            val lastCheckHour = prefs.getInt(KEY_LAST_POWER_CHECK_HOUR, -1)

            var shouldCheckPower = false

            if (lastCheckDateStr.isEmpty() || lastCheckHour == -1) {
                shouldCheckPower = true
            } else {
                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                try {
                    val lastDate = sdf.parse(lastCheckDateStr)
                    if (lastDate != null) {
                        val cal = Calendar.getInstance()
                        cal.time = lastDate
                        cal.set(Calendar.HOUR_OF_DAY, lastCheckHour)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)

                        // Giờ check tiếp theo = giờ check lần trước + 1h + 1m
                        cal.add(Calendar.HOUR_OF_DAY, 1)
                        cal.add(Calendar.MINUTE, 1)

                        val targetCheckTime = cal.timeInMillis
                        if (System.currentTimeMillis() >= targetCheckTime) {
                            shouldCheckPower = true
                        }
                    } else {
                        shouldCheckPower = true
                    }
                } catch (e: Exception) {
                    shouldCheckPower = true
                }
            }

            Log.d(
                TAG,
                "syncLogs() - sending listDir with unique reqId, shouldCheckPower=$shouldCheckPower"
            )

            // Gửi reqId độc quyền cho toggle listDir
            activeToggleListDirReqId = remote.listDir("/logs/toggle/")

            // Chỉ listDir power khi đúng thời gian
            if (shouldCheckPower) {
                activePowerListDirReqId = remote.listDir("/logs/power/")
            } else {
                Log.d(TAG, "syncLogs() - Skip power listDir because time check rule is not met yet")
            }
        }
    }

    private fun handleListDirResult(event: PumpCommandEvent.ListDirResult) {
        if (!event.success) {
            Log.e(TAG, "ListDir failed for ${event.path}: ${event.message}")
            _isSyncing.value = false
            return
        }

        // Bắt buộc phải có reqId và reqId phải khớp với reqId mà máy này đã phát đi
        if (event.reqId.isNullOrEmpty()) {
            Log.w(TAG, "Ignoring listDir response for ${event.path}: reqId is missing")
            return
        }

        if (event.path.contains("toggle") && event.reqId != activeToggleListDirReqId) {
            Log.w(TAG, "Ignoring listDir response for toggle: reqId ${event.reqId} != expected $activeToggleListDirReqId")
            return
        }
        if (event.path.contains("power") && event.reqId != activePowerListDirReqId) {
            Log.w(TAG, "Ignoring listDir response for power: reqId ${event.reqId} != expected $activePowerListDirReqId")
            return
        }

        val todayDateStr = getTodayDateStr()
        val cal = Calendar.getInstance()
        val curHour = cal.get(Calendar.HOUR_OF_DAY)

        if (event.path.contains("power")) {
            prefs.edit()
                .putString(KEY_LAST_POWER_CHECK_DATE, todayDateStr)
                .putInt(KEY_LAST_POWER_CHECK_HOUR, curHour)
                .apply()

            event.entries.filter { it.name.endsWith(".log") && !it.name.startsWith("nosync") }.forEach { entry ->
                val fullPath = "/logs/power/${entry.name}"
                val deviceSize = entry.size
                val savedContent = prefs.getString(KEY_RAW_CONTENT + fullPath, "") ?: ""
                val savedSize = savedContent.toByteArray(Charsets.UTF_8).size.toLong()

                if (deviceSize > savedSize) {
                    Log.d(
                        TAG,
                        "Power log $fullPath has NEW data (deviceSize=$deviceSize > savedSize=$savedSize). Requesting offset=$savedSize"
                    )
                    scope.launch {
                        val reqId = remote.readFile(fullPath, offset = savedSize, limit = 1024, encode = false)
                        activeReadFileSessions[fullPath] = FileReadSession(reqId = reqId, expectedOffset = savedSize)
                    }
                } else {
                    Log.d(
                        TAG,
                        "Power log $fullPath UNCHANGED (deviceSize=$deviceSize <= savedSize=$savedSize)."
                    )
                    if (savedContent.isNotEmpty()) {
                        val dateStr = entry.name.removeSuffix(".log")
                        val dailyLog = parsePowerLogData(dateStr, savedContent)
                        val finalLog = mergeWithRealtimeData(dailyLog)
                        savePowerLogToCache(finalLog)
                    }
                }
            }
        } else if (event.path.contains("toggle")) {
            event.entries.filter { it.name.endsWith(".log") && !it.name.startsWith("nosync") }.forEach { entry ->
                val fullPath = "/logs/toggle/${entry.name}"
                val deviceSize = entry.size
                val savedContent = prefs.getString(KEY_RAW_CONTENT + fullPath, "") ?: ""
                val savedSize = savedContent.toByteArray(Charsets.UTF_8).size.toLong()

                if (deviceSize > savedSize) {
                    Log.d(
                        TAG,
                        "Toggle log $fullPath has NEW data (deviceSize=$deviceSize > savedSize=$savedSize). Requesting offset=$savedSize"
                    )
                    scope.launch {
                        val reqId = remote.readFile(fullPath, offset = savedSize, limit = 1024, encode = false)
                        activeReadFileSessions[fullPath] = FileReadSession(reqId = reqId, expectedOffset = savedSize)
                    }
                } else {
                    Log.d(
                        TAG,
                        "Toggle log $fullPath UNCHANGED (deviceSize=$deviceSize <= savedSize=$savedSize)."
                    )
                    if (savedContent.isNotEmpty()) {
                        val dateStr = entry.name.removeSuffix(".log")
                        val events = parseToggleLogData(savedContent)
                        saveToggleLogToCache(dateStr, events)
                    }
                }
            }
        }
        _isSyncing.value = false
    }

    private fun handleReadFileResult(event: PumpCommandEvent.ReadFileResult) {
        val fullPath = event.path
        if (!event.success) {
            Log.e(TAG, "ReadFile failed for $fullPath: ${event.message}")
            activeReadFileSessions.remove(fullPath)
            pendingFileBuffers.remove(fullPath)
            return
        }

        // Bắt buộc phải có reqId
        if (event.reqId.isNullOrEmpty()) {
            Log.w(TAG, "Ignoring ReadFileResult for $fullPath: reqId is missing")
            return
        }

        val session = activeReadFileSessions[fullPath]

        // 1. Phải khớp với reqId của session hiện tại trên máy này
        if (session == null || event.reqId != session.reqId) {
            Log.w(TAG, "Ignoring ReadFileResult for $fullPath: reqId ${event.reqId} != expected ${session?.reqId}")
            return
        }

        // 2. Phải khớp offset mong muốn
        if (event.offset != session.expectedOffset) {
            Log.w(TAG, "Ignoring ReadFileResult for $fullPath: offset ${event.offset} != expected ${session.expectedOffset}")
            return
        }

        val filename = fullPath.substringAfterLast("/")
        val dateStr = filename.removeSuffix(".log")
        if (dateStr.isBlank()) return

        // Nối dữ liệu mới tải từ offset vào dữ liệu raw đã lưu dưới máy
        val savedContent = prefs.getString(KEY_RAW_CONTENT + fullPath, "") ?: ""

        val buffer = pendingFileBuffers.getOrPut(fullPath) {
            StringBuilder(
                if (event.offset == 0L) {
                    ""
                } else if (event.offset <= savedContent.length.toLong()) {
                    savedContent.substring(0, event.offset.toInt())
                } else {
                    savedContent
                }
            )
        }

        buffer.append(event.data)

        val currentAccumulatedText = buffer.toString()

        if (event.more) {
            val nextOffset = currentAccumulatedText.toByteArray(Charsets.UTF_8).size.toLong()
            Log.d(
                TAG,
                "ReadFile path=$fullPath has MORE data (total size=${event.size}). Requesting next chunk at offset=$nextOffset"
            )
            scope.launch {
                val nextReqId = remote.readFile(fullPath, offset = nextOffset, limit = 1024, encode = false)
                activeReadFileSessions[fullPath] = FileReadSession(reqId = nextReqId, expectedOffset = nextOffset)
            }
            return
        }

        // Tải hoàn tất tất cả các chunk mới của file
        activeReadFileSessions.remove(fullPath)
        val fullContent = currentAccumulatedText
        pendingFileBuffers.remove(fullPath)

        // Lưu dữ liệu raw xuống máy để các lần mở app sau không bị mất
        prefs.edit().putString(KEY_RAW_CONTENT + fullPath, fullContent).apply()

        if (fullPath.contains("/power/")) {
            val dailyLog = parsePowerLogData(dateStr, fullContent)
            val finalLog = mergeWithRealtimeData(dailyLog)
            savePowerLogToCache(finalLog)
        } else if (fullPath.contains("/toggle/")) {
            val toggleEvents = parseToggleLogData(fullContent)
            saveToggleLogToCache(dateStr, toggleEvents)
        }
    }

    // ── Parsers ──

    /**
     * Format file power: mỗi dòng chứa `hour|powerWh` (VD: 0|100 ... 23|75)
     */
    private fun parsePowerLogData(dateStr: String, content: String): DailyEnergyLog {
        val hourlyMap = (0..23).associateWith { 0L }.toMutableMap()
        content.lines().forEach { line ->
            val parts = line.trim().split("|")
            if (parts.size >= 2) {
                val hour = parts[0].trim().toIntOrNull()
                val powerWh = parts[1].trim().toLongOrNull()
                if (hour != null && hour in 0..23 && powerWh != null) {
                    hourlyMap[hour] = powerWh
                }
            }
        }

        val hourlyList = (0..23).map { h -> HourlyEnergyLog(h, hourlyMap[h] ?: 0L) }
        val totalWh = hourlyList.sumOf { it.energyWh }
        val isToday = (dateStr == getTodayDateStr())

        return DailyEnergyLog(
            dateStr = dateStr,
            totalWh = totalWh,
            hourlyList = hourlyList,
            isCompleteDay = !isToday
        )
    }

    /**
     * Format file toggle: mỗi dòng chứa `time|ToggleSource|state` (VD: 02:15:48.190|1|0)
     */
    private fun parseToggleLogData(content: String): List<ToggleLogEvent> {
        val list = mutableListOf<ToggleLogEvent>()
        content.lines().forEach { line ->
            val parts = line.trim().split("|")
            if (parts.size >= 3) {
                val timeStr = parts[0].trim()
                val srcCode = parts[1].trim().toIntOrNull() ?: -1
                val stateCode = parts[2].trim()
                val stateBool = (stateCode == "1" || stateCode.equals("true", ignoreCase = true))

                if (timeStr.isNotBlank()) {
                    list.add(
                        ToggleLogEvent(
                            timeStr = timeStr,
                            source = ToggleSource.fromCode(srcCode),
                            state = stateBool
                        )
                    )
                }
            }
        }
        return list
    }

    // ── Cache saving & parsing helpers ──

    private fun savePowerLogToCache(dailyLog: DailyEnergyLog) {
        val json = JSONObject().apply {
            put("totalWh", dailyLog.totalWh)
            put("isComplete", dailyLog.isCompleteDay)
            val arr = JSONArray()
            dailyLog.hourlyList.forEach { h ->
                arr.put(JSONObject().apply {
                    put("hour", h.hour)
                    put("wh", h.energyWh)
                })
            }
            put("hourly", arr)
        }

        prefs.edit().putString(PREFIX_POWER + dailyLog.dateStr, json.toString()).apply()

        val updatedMap = _dailyPowerLogs.value.toMutableMap()
        updatedMap[dailyLog.dateStr] = dailyLog
        _dailyPowerLogs.value = updatedMap

        val updatedDates = sortDates((_availableDates.value.toSet() + dailyLog.dateStr).toList())
        _availableDates.value = updatedDates
    }

    private fun parseCachedPowerJson(dateStr: String, jsonStr: String): DailyEnergyLog? {
        return try {
            val json = JSONObject(jsonStr)
            val totalWh = json.optLong("totalWh", 0L)
            val isComplete = json.optBoolean("isComplete", false)
            val arr = json.optJSONArray("hourly")
            val hourlyMap = (0..23).associateWith { 0L }.toMutableMap()

            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val hour = item.optInt("hour", -1)
                    val wh = item.optLong("wh", 0L)
                    if (hour in 0..23) {
                        hourlyMap[hour] = wh
                    }
                }
            }

            val hourlyList = (0..23).map { h -> HourlyEnergyLog(h, hourlyMap[h] ?: 0L) }

            DailyEnergyLog(
                dateStr = dateStr,
                totalWh = totalWh,
                hourlyList = hourlyList,
                isCompleteDay = isComplete
            )
        } catch (ex: Exception) {
            null
        }
    }

    private fun saveToggleLogToCache(dateStr: String, events: List<ToggleLogEvent>) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("timeStr", e.timeStr)
                put("source", e.source.code)
                put("state", e.state)
            })
        }

        prefs.edit().putString(PREFIX_TOGGLE + dateStr, arr.toString()).apply()

        val updatedMap = _dailyToggleLogs.value.toMutableMap()
        updatedMap[dateStr] = events
        _dailyToggleLogs.value = updatedMap

        val updatedDates = sortDates((_availableDates.value.toSet() + dateStr).toList())
        _availableDates.value = updatedDates
    }

    private fun parseCachedToggleJson(jsonStr: String): List<ToggleLogEvent> {
        val list = mutableListOf<ToggleLogEvent>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val timeStr = item.optString("timeStr", "")
                val srcCode = item.optInt("source", -1)
                val stateBool = item.optBoolean("state", false)
                if (timeStr.isNotBlank()) {
                    list.add(
                        ToggleLogEvent(
                            timeStr = timeStr,
                            source = ToggleSource.fromCode(srcCode),
                            state = stateBool
                        )
                    )
                }
            }
        } catch (ex: Exception) {
            // Ignore format error
        }
        return list
    }

    private fun mergeWithRealtimeData(parsedLog: DailyEnergyLog): DailyEnergyLog {
        // Dữ liệu từ file log luôn là sự thật cuối cùng cho các giờ đã qua.
        // Trả về trực tiếp parsedLog để:
        // 1. Xóa giờ lưu tạm cũ, thay bằng dữ liệu chính thức từ file.
        // 2. Tự động reset giá trị tạm của giờ mới về 0.
        // Sau đó getStatus sẽ cập nhật lại giá trị tạm mới vào RAM.
        return parsedLog
    }

    private fun handleStatusUpdate(event: PumpCommandEvent.StatusUpdate) {
        val realtimeEnergyWh = event.status.hourlyEnergy.toLong()
        if (realtimeEnergyWh <= 0L) return

        val todayStr = getTodayDateStr()
        val curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val currentMap = _dailyPowerLogs.value
        val originalDailyLog = currentMap[todayStr] ?: DailyEnergyLog(
            dateStr = todayStr,
            totalWh = 0L,
            hourlyList = (0..23).map { HourlyEnergyLog(it, 0L) },
            isCompleteDay = false
        )

        val currentStoredEnergy = originalDailyLog.hourlyList.find { it.hour == curHour }?.energyWh ?: 0L

        // Chỉ cập nhật nếu giá trị realtime lớn hơn giá trị đang lưu 
        // (tránh trường hợp ghi đè bằng 0 khi thiết bị vừa chuyển sang giờ mới nhưng app chưa kịp tải file log)
        if (realtimeEnergyWh > currentStoredEnergy) {
            val updatedHourlyList = originalDailyLog.hourlyList.map {
                if (it.hour == curHour) it.copy(energyWh = realtimeEnergyWh)
                else it
            }
            val updatedDailyLog = originalDailyLog.copy(
                hourlyList = updatedHourlyList,
                totalWh = updatedHourlyList.sumOf { it.energyWh }
            )
            savePowerLogToCache(updatedDailyLog)
        }
    }

    // ── Public Accessors ──

    /**
     * Lấy dữ liệu 6 tháng gần nhất để vẽ biểu đồ tháng.
     */
    fun get6MonthEnergyLogs(): List<MonthlyEnergyLog> {
        val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val sdfMonth = SimpleDateFormat("MM/yyyy", Locale.US)
        val monthlyTotals = mutableMapOf<String, Long>()

        _dailyPowerLogs.value.forEach { (dateStr, dailyLog) ->
            try {
                val date = sdfDate.parse(dateStr)
                if (date != null) {
                    val monthKey = sdfMonth.format(date)
                    monthlyTotals[monthKey] = (monthlyTotals[monthKey] ?: 0L) + dailyLog.totalWh
                }
            } catch (ex: Exception) {
                // Ignore parse error
            }
        }

        // Tạo 6 tháng gần nhất (gồm cả tháng hiện tại)
        val cal = Calendar.getInstance()
        val result = mutableListOf<MonthlyEnergyLog>()
        for (i in 5 downTo 0) {
            val tempCal = cal.clone() as Calendar
            tempCal.add(Calendar.MONTH, -i)
            val monthKey = sdfMonth.format(tempCal.time)
            result.add(MonthlyEnergyLog(monthKey, monthlyTotals[monthKey] ?: 0L))
        }
        return result
    }

    companion object {
        private const val TAG = "LogRepository"
        private const val PREFS_NAME = "energy_toggle_log_cache"
        private const val PREFIX_POWER = "power_log_"
        private const val PREFIX_TOGGLE = "toggle_log_"
        private const val KEY_RAW_CONTENT = "raw_content_"
        private const val KEY_LAST_POWER_CHECK_DATE = "last_power_check_date"
        private const val KEY_LAST_POWER_CHECK_HOUR = "last_power_check_hour"

        fun getTodayDateStr(): String {
            return SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        }

        private fun sortDates(dates: List<String>): List<String> {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            return dates.sortedByDescending {
                runCatching { sdf.parse(it) }.getOrNull() ?: Date(0)
            }
        }
    }
}
