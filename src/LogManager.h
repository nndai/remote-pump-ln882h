#pragma once

#include <Arduino.h>
#include <FS.h>
#include <LittleFS.h>
#include <functional>
#include <FreeRTOS.h>
#include <queue.h>
#include <task.h>

class LogManager {
public:
    using LogCallback = std::function<void(const String& line)>;

    enum ToggleSource : uint8_t {
        TOGGLE_BUTTON   = 0,
        TOGGLE_ONLINE   = 1,
        TOGGLE_SCHEDULE = 2,
    };

    struct DataFile {
        uint32_t buttonCount;
        uint32_t toggleCount;
        uint64_t totalPower;
        uint32_t totalPumpSeconds;
    };

    LogManager();
    bool begin();

    void ingest(const char* line);
    void writeFile(const char* line);
    void logToggle(ToggleSource src, bool on);
    void logPower(unsigned long power);
    void logHourlyPower(uint8_t hour, uint32_t energyWh, time_t intervalEpoch = 0);

    void loadData();
    void saveData();
    const DataFile& getData() const;
    void addButtonCount(uint32_t count = 1);
    void addToggleCount(uint32_t count = 1);
    void addTotalPower(uint64_t power);
    void addPumpTime(uint32_t seconds);

    void setTime(unsigned long epoch);
    bool isTimeSynced() const;
    unsigned long getEpoch() const;

    void maintenance();

    size_t getSysLogSize();
    size_t getToggleLogSize();
    size_t getPowerLogSize();
    size_t getTotalBytes();
    size_t getUsedBytes();

    bool readSysLog(String& out, size_t maxBytes = 4096);
    bool readToggleLog(String& out, size_t maxBytes = 4096);
    bool readPowerLog(String& out, size_t maxBytes = 4096);

    void clearSysLog();
    void clearToggleLog();
    void clearPowerLog();

    void setLogCallback(LogCallback cb);
    void setSysLogFileEnabled(bool enable);
    void setSysLogFileLevel(uint8_t level);

private:
    QueueHandle_t _logQueue;
    TaskHandle_t _writerTaskHandle;

    static void _writerTask(void* param);

    bool _mounted;
    bool _sysLogFileEnabled = true;
    bool _timeSynced;
    unsigned long _epoch;
    unsigned long _epochMillis;
    unsigned long _lastMaintenance;
    uint8_t _sysLogFileLevel = LT_LEVEL_INFO;
    LogCallback _logCb;
    DataFile _data;

    struct LogQueueEntry {
        char* line;
        char* path;
    };

    static constexpr size_t LOG_QUEUE_SIZE = 128;

    // ── System log: mỗi file tối đa 10KB, mỗi dòng tối đa 100 ký tự, tối đa 5 file ──
    static constexpr size_t SYS_MAX_FILE_SIZE = 10 * 1024;
    static constexpr size_t SYS_MAX_LINE_LEN  = 100;
    static constexpr size_t SYS_MAX_FILES     = 5;

    // ── Toggle log: không giới hạn số file, chỉ giới hạn tổng folder 100KB ──
    static constexpr size_t TOGGLE_MAX_FOLDER = 100 * 1024;

    // ── Power log: không giới hạn số file, chỉ giới hạn tổng folder 200KB ──
    static constexpr size_t POWER_MAX_FOLDER  = 200 * 1024; 

    static constexpr const char* DIR_SYS     = "/logs/sys/";
    static constexpr const char* DIR_TOGGLE  = "/logs/toggle/";
    static constexpr const char* DIR_POWER   = "/logs/power/";
    static constexpr const char* FILE_DATA   = "/logs/data.bin";

    String _ts();
    String _dateStr();
    String _sysPath();
    String _togglePath();
    String _powerPath();
    void _writeLine(const String& path, const char* line);
    void _appendFile(const String& src, const String& dst, size_t maxSize);
    void _writeToggleLine(ToggleSource src, const char* state);
    void _writePowerLine(unsigned long power);
    void _rotateSys();
    void _rotateToggle();
    void _rotatePower();
    void _cleanDir(const String& dir, size_t budget);
    static size_t _dirBytes(const String& dir);
    static int _listLogFiles(const String& dir, String* files, int maxCount);
    static void _sortLogFilesByDate(String* files, int count);
};
