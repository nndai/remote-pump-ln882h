#include "LogManager.h"
#include <time.h>
#include <Config.h>
#include <algorithm>

LogManager::LogManager()
    : _mounted(false)
    , _timeSynced(false)
    , _epoch(0)
    , _epochMillis(0)
    , _lastMaintenance(0)
{
}

bool LogManager::begin() {
    if (!LITTLEFS.begin()) return false;
    _mounted = true;

    for (const char* d : {DIR_SYS, DIR_TOGGLE, DIR_POWER}) {
        if (!LITTLEFS.exists(d)) LITTLEFS.mkdir(d);
    }

    if (LITTLEFS.exists("/logs/nosync.log")) {
        LITTLEFS.remove("/logs/nosync.log");
    }

    String nosync = String(DIR_SYS) + "nosync.log";
    if (LITTLEFS.exists(nosync)) {
        LITTLEFS.remove(nosync);
    }

    String nosyncPower = String(DIR_POWER) + "nosync.log";
    if (LITTLEFS.exists(nosyncPower)) {
        LITTLEFS.remove(nosyncPower);
    }

    _logQueue = xQueueCreate(LOG_QUEUE_SIZE, sizeof(LogQueueEntry));
    if (_logQueue) {
        xTaskCreate(_writerTask, "logWriter", 1024, this, tskIDLE_PRIORITY + 1, &_writerTaskHandle);
    }

    loadData();
    _lastMaintenance = millis();
    return true;
}

String LogManager::_ts() {
    if (_timeSynced) {
        unsigned long now = millis();
        time_t raw = getEpoch();
        unsigned int ms = (now - _epochMillis) % 1000;
        struct tm ti;
        gmtime_r(&raw, &ti);

        char buf[16];
        snprintf(buf, sizeof(buf), "%02u:%02u:%02u.%03u",
                 ti.tm_hour, ti.tm_min, ti.tm_sec, ms);
        return String(buf);
    }

    unsigned long ms = millis();
    unsigned long t = ms / 1000;
    char buf[16];
    snprintf(buf, sizeof(buf), "%02lu:%02lu:%02lu.%03lu",
             t / 3600, (t / 60) % 60, t % 60, ms % 1000);
    return String(buf);
}

String LogManager::_dateStr() {
    if (_timeSynced) {
        time_t raw = getEpoch();
        struct tm ti;
        gmtime_r(&raw, &ti);
        char buf[12];
        snprintf(buf, sizeof(buf), "%02d-%02d-%04d", ti.tm_mday, ti.tm_mon + 1, ti.tm_year + 1900);
        return String(buf);
    }
    return "nosync";
}

String LogManager::_sysPath() {
    return String(DIR_SYS) + _dateStr() + ".log";
}

String LogManager::_togglePath() {
    if (_timeSynced) {
        return String(DIR_TOGGLE) + _dateStr() + ".log";
    }
    String files[32];
    int count = _listLogFiles(DIR_TOGGLE, files, 32);
    if (count > 0) {
        _sortLogFilesByDate(files, count);
        return String(DIR_TOGGLE) + files[count - 1];
    }
    return String(DIR_TOGGLE) + "01-01-1970.log";
}

String LogManager::_powerPath() {
    return String(DIR_POWER) + _dateStr() + ".log";
}

void LogManager::_writeLine(const String& path, const char* line) {
    if (!_mounted) return;

    _rotateSys();

    File fCheck = LITTLEFS.open(path, "r");
    if (fCheck) {
        if (fCheck.size() >= SYS_MAX_FILE_SIZE) {
            fCheck.close();
            return;
        }
        fCheck.close();
    }

    File f = LITTLEFS.open(path, "a");
    if (!f) return;

    String ts = _ts();
    f.print(ts);
    f.print(" ");

    size_t len = strlen(line);
    if (len > SYS_MAX_LINE_LEN) {
        f.write((const uint8_t*)line, SYS_MAX_LINE_LEN);
        f.print("\n");
    } else {
        f.print(line);
        f.print("\n");
    }

    f.close();
}

void LogManager::_appendFile(const String& src, const String& dst, size_t maxSize) {
    size_t dstSize = 0;
    {
        File fCheck = LITTLEFS.open(dst, "r");
        if (fCheck) {
            dstSize = fCheck.size();
            fCheck.close();
        }
    }
    if (maxSize > 0 && dstSize >= maxSize) return;

    File fSrc = LITTLEFS.open(src, "r");
    if (!fSrc) return;
    File fDst = LITTLEFS.open(dst, "a");
    if (!fDst) { fSrc.close(); return; }

    if (maxSize > 0) {
        size_t remaining = maxSize - dstSize;
        uint8_t buf[128];
        int n;
        while (remaining > 0 && (n = fSrc.read(buf, sizeof(buf))) > 0) {
            size_t toWrite = (size_t)n < remaining ? (size_t)n : remaining;
            fDst.write(buf, toWrite);
            remaining -= toWrite;
        }
    } else {
        uint8_t buf[128];
        int n;
        while ((n = fSrc.read(buf, sizeof(buf))) > 0) {
            fDst.write(buf, n);
        }
    }

    fSrc.close();
    fDst.close();
}

int LogManager::_listLogFiles(const String& dir, String* files, int maxCount) {
    File d = LITTLEFS.open(dir);
    if (!d || !d.isDirectory()) {
        if (d) d.close();
        return 0;
    }
    int count = 0;
    File f = d.openNextFile();
    while (f && count < maxCount) {
        String name = String(f.name());
        f.close();
        if (name.endsWith(".log") && name.length() == 14) {
            files[count++] = name;
        }
        f = d.openNextFile();
    }
    d.close();
    return count;
}

void LogManager::_sortLogFilesByDate(String* files, int count) {
    if (count <= 1) return;
    std::sort(files, files + count, [](const String& a, const String& b) {
        int da, ma, ya, db, mb, yb;
        if (sscanf(a.c_str(), "%d-%d-%d", &da, &ma, &ya) != 3) return false;
        if (sscanf(b.c_str(), "%d-%d-%d", &db, &mb, &yb) != 3) return true;
        if (ya != yb) return ya < yb;
        if (ma != mb) return ma < mb;
        return da < db;
    });
}

void LogManager::_rotateSys() {
    String files[SYS_MAX_FILES + 1];
    int count = _listLogFiles(DIR_SYS, files, SYS_MAX_FILES + 1);
    if (count < SYS_MAX_FILES) return;
    _sortLogFilesByDate(files, count);
    int toDelete = count - (SYS_MAX_FILES - 1);
    for (int i = 0; i < toDelete; i++) {
        LITTLEFS.remove(String(DIR_SYS) + files[i]);
    }
}

void LogManager::ingest(const char* line) {
    if (!line) return;

    if (_logCb) _logCb(String(line));

    writeFile(line);
}

void LogManager::writeFile(const char* line) {
    if (!line || !_mounted || !_sysLogFileEnabled || !_logQueue) return;

    uint8_t level = LT_LEVEL_INFO;
    char c = line[0];
    if (c == 'T') level = LT_LEVEL_TRACE;
    else if (c == 'D') level = LT_LEVEL_DEBUG;
    else if (c == 'I') level = LT_LEVEL_INFO;
    else if (c == 'W') level = LT_LEVEL_WARN;
    else if (c == 'E') level = LT_LEVEL_ERROR;
    else if (c == 'F') level = LT_LEVEL_FATAL;

    if (level < _sysLogFileLevel) return;

    char* lineCopy = strdup(line);
    if (!lineCopy) return;

    String path = _sysPath();
    char* pathCopy = strdup(path.c_str());
    if (!pathCopy) { free(lineCopy); return; }

    LogQueueEntry entry = {lineCopy, pathCopy};
    if (xQueueSend(_logQueue, &entry, 0) != pdTRUE) {
        free(lineCopy);
        free(pathCopy);
    }
}

void LogManager::_writerTask(void* param) {
    LogManager* self = static_cast<LogManager*>(param);
    LogQueueEntry entry;

    while (1) {
        if (xQueueReceive(self->_logQueue, &entry, portMAX_DELAY) == pdTRUE) {
            if (entry.line && entry.path) {
                self->_writeLine(entry.path, entry.line);
            }
            free(entry.line);
            free(entry.path);
        }
    }
}

void LogManager::logToggle(ToggleSource src, bool on) {
    if (!_mounted) return;
    _rotateToggle();
    _writeToggleLine(src, on ? "1" : "0");
    if (src == TOGGLE_BUTTON) _data.buttonCount++;
    _data.toggleCount++;
    saveData();
}

void LogManager::_writeToggleLine(ToggleSource src, const char* state) {
    if (!_mounted) return;

    String path = _togglePath();

    File f = LITTLEFS.open(path, "a");
    if (!f) return;

    if (_timeSynced) {
        String ts = _ts();
        ts.remove(ts.length() - 4); // bỏ ".sss" milliseconds
        f.print(ts);
    } else {
        f.print("?");
        f.print(millis() / 1000);
        f.print("?");
    }
    f.print("|");
    f.print((uint8_t)src);
    f.print("|");
    f.print(state);
    f.print("\n");
    f.close();
}

void LogManager::_rotateToggle() {
    size_t total = _dirBytes(DIR_TOGGLE);
    if (total <= TOGGLE_MAX_FOLDER) return;

    String files[32];
    int count = _listLogFiles(DIR_TOGGLE, files, 32);
    if (count == 0) return;

    _sortLogFilesByDate(files, count);

    int toDelete = count / 3;
    if (toDelete < 1) toDelete = 1;
    for (int i = 0; i < toDelete; i++) {
        LITTLEFS.remove(String(DIR_TOGGLE) + files[i]);
    }
}

void LogManager::logPower(unsigned long power) {
    if (!_mounted) return;
    _rotatePower();
    _writePowerLine(power);
}

void LogManager::logHourlyPower(uint8_t hour, uint32_t energyWh, time_t intervalEpoch) {
    if (!_mounted) return;
    _rotatePower();

    String path;
    if (intervalEpoch > 0) {
        struct tm ti;
        gmtime_r(&intervalEpoch, &ti);
        char buf[12];
        snprintf(buf, sizeof(buf), "%02d-%02d-%04d",
                 ti.tm_mday, ti.tm_mon + 1, ti.tm_year + 1900);
        path = String(DIR_POWER) + buf + ".log";
    } else {
        path = _powerPath();
    }

    File f = LITTLEFS.open(path, "a");
    if (!f) return;
    f.print(hour);
    f.print("|");
    f.print(energyWh);
    f.print("\n");
    f.close();
}

void LogManager::_writePowerLine(unsigned long power) {
    if (!_mounted) return;

    String path = _powerPath();

    File f = LITTLEFS.open(path, "a");
    if (!f) return;

    String ts = _ts();
    f.print(ts);
    f.print("|");
    f.print(power);
    f.print("\n");
    f.close();
}

void LogManager::_rotatePower() {
    size_t total = _dirBytes(DIR_POWER);
    if (total <= POWER_MAX_FOLDER) return;

    String files[32];
    int count = _listLogFiles(DIR_POWER, files, 32);
    if (count == 0) return;

    _sortLogFilesByDate(files, count);

    int toDelete = count / 3;
    if (toDelete < 1) toDelete = 1;
    for (int i = 0; i < toDelete; i++) {
        LITTLEFS.remove(String(DIR_POWER) + files[i]);
    }
}

void LogManager::setLogCallback(LogCallback cb) {
    _logCb = cb;
}

void LogManager::setSysLogFileEnabled(bool enable) {
    _sysLogFileEnabled = enable;
}

void LogManager::setSysLogFileLevel(uint8_t level) {
    _sysLogFileLevel = level;
}

void LogManager::setTime(unsigned long epoch) {
    if (epoch > 1700000000) {
        if (!_timeSynced) {
            // Lần đầu đồng bộ: nối nosync log vào file date
            String nosyncSys = String(DIR_SYS) + "nosync.log";
            if (LITTLEFS.exists(nosyncSys)) {
                String dest = _sysPath();
                if (LITTLEFS.exists(dest)) {
                    _appendFile(nosyncSys, dest, SYS_MAX_FILE_SIZE);
                    LITTLEFS.remove(nosyncSys);
                } else {
                    LITTLEFS.rename(nosyncSys, dest);
                }
            }

            String nosyncPower = String(DIR_POWER) + "nosync.log";
            if (LITTLEFS.exists(nosyncPower)) {
                String dest = _powerPath();
                if (LITTLEFS.exists(dest)) {
                    _appendFile(nosyncPower, dest, 0);
                    LITTLEFS.remove(nosyncPower);
                } else {
                    LITTLEFS.rename(nosyncPower, dest);
                }
            }
        }
        _epochMillis = millis();
        _epoch = epoch;
        _timeSynced = true;
    } else {
        _timeSynced = false;
    }
}

bool LogManager::isTimeSynced() const { return _timeSynced; }
unsigned long LogManager::getEpoch() const {
    
    unsigned long elapsed = (millis() - _epochMillis) / 1000;
    if (!_timeSynced) return (millis() / 1000);
    return _epoch + elapsed;
}

void LogManager::maintenance() {
    if (!_mounted) return;
    unsigned long now = millis();
    if (now - _lastMaintenance < 3600000UL) return;
    _lastMaintenance = now;

    _rotateSys();
}

size_t LogManager::getSysLogSize()    { return _dirBytes(DIR_SYS); }
size_t LogManager::getToggleLogSize() { return _dirBytes(DIR_TOGGLE); }
size_t LogManager::getPowerLogSize()  { return _dirBytes(DIR_POWER); }
size_t LogManager::getTotalBytes()    { return LITTLEFS.totalBytes(); }
size_t LogManager::getUsedBytes()     { return LITTLEFS.usedBytes(); }

static bool readDirConcat(const String& dir, String& out, size_t maxBytes) {
    out = "";
    File d = LITTLEFS.open(dir);
    if (!d || !d.isDirectory()) return false;
    File f = d.openNextFile();
    while (f) {
        String p = dir + String(f.name());
        f.close();
        File rf = LITTLEFS.open(p, "r");
        if (rf) {
            size_t remain = maxBytes - out.length();
            if (remain > 0) {
                uint8_t* buf = new uint8_t[remain + 1];
                size_t r = rf.read(buf, remain);
                buf[r] = 0;
                out += (const char*)buf;
                delete[] buf;
            }
            rf.close();
            if (out.length() >= maxBytes) break;
        }
        f = d.openNextFile();
    }
    d.close();
    return out.length() > 0;
}

bool LogManager::readSysLog(String& out, size_t maxBytes)    { return readDirConcat(DIR_SYS, out, maxBytes); }
bool LogManager::readToggleLog(String& out, size_t maxBytes) { return readDirConcat(DIR_TOGGLE, out, maxBytes); }
bool LogManager::readPowerLog(String& out, size_t maxBytes)  { return readDirConcat(DIR_POWER, out, maxBytes); }

static void clearDir(const String& dir) {
    File d = LITTLEFS.open(dir);
    if (!d || !d.isDirectory()) return;
    File f = d.openNextFile();
    while (f) {
        String p = dir + String(f.name());
        f.close();
        LITTLEFS.remove(p);
        f = d.openNextFile();
    }
    d.close();
}

void LogManager::clearSysLog()    { clearDir(DIR_SYS); }
void LogManager::clearToggleLog() { clearDir(DIR_TOGGLE); }
void LogManager::clearPowerLog()  { clearDir(DIR_POWER); }

size_t LogManager::_dirBytes(const String& dir) {
    size_t total = 0;
    File d = LITTLEFS.open(dir);
    if (!d || !d.isDirectory()) return 0;
    File f = d.openNextFile();
    while (f) {
        total += f.size();
        f.close();
        f = d.openNextFile();
    }
    d.close();
    return total;
}

void LogManager::_cleanDir(const String& dir, size_t budget) {
    (void)dir;
    (void)budget;
}

void LogManager::loadData() {
    memset(&_data, 0, sizeof(_data));
    if (!_mounted) return;

    File f = LITTLEFS.open(FILE_DATA, "r");
    if (f) {
        if (f.size() == sizeof(DataFile)) {
            f.read((uint8_t*)&_data, sizeof(DataFile));
        }
        f.close();
    }
}

void LogManager::saveData() {
    if (!_mounted) return;

    File f = LITTLEFS.open(FILE_DATA, "w");
    if (!f) return;
    f.write((const uint8_t*)&_data, sizeof(DataFile));
    f.close();
}

const LogManager::DataFile& LogManager::getData() const {
    return _data;
}

void LogManager::addButtonCount(uint32_t count) {
    _data.buttonCount += count;
    saveData();
}

void LogManager::addToggleCount(uint32_t count) {
    _data.toggleCount += count;
    saveData();
}

void LogManager::addTotalPower(uint64_t power) {
    _data.totalPower += power;
    saveData();
}

void LogManager::addPumpTime(uint32_t seconds) {
    _data.totalPumpSeconds += seconds;
    saveData();
}
