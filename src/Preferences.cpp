#include <Arduino.h>
#include <Preferences.h>
#include <LittleFS.h>
#include <cstring>
#include <map>

struct PrefState {
    String name;
    bool readOnly;
};

static std::map<IPreferences*, PrefState> s_states;

static PrefState& state(IPreferences* obj) {
    return s_states[obj];
}

static String prefPath(const String& ns, const char* key) {
    return String("/pref/") + ns + "/" + key;
}

bool IPreferences::begin(const char* name, bool readOnly, const char* partition_label) {
    (void)partition_label;
    auto& s = state(this);
    s.name = name;
    s.readOnly = readOnly;
    return true;
}

void IPreferences::end() {
    s_states.erase(this);
}

bool IPreferences::clear() {
    auto& s = state(this);
    String dir = String("/pref/") + s.name + "/";
    File d = LITTLEFS.open(dir);
    if (!d || !d.isDirectory()) return false;
    File f = d.openNextFile();
    while (f) {
        String p = f.path();
        f.close();
        LITTLEFS.remove(p);
        f = d.openNextFile();
    }
    d.close();
    return true;
}

bool IPreferences::remove(const char* key) {
    auto& s = state(this);
    return LITTLEFS.remove(prefPath(s.name, key));
}

size_t IPreferences::putBytes(const char* key, const void* value, size_t len) {
    auto& s = state(this);
    if (s.readOnly) return 0;
    LITTLEFS.mkdir(String("/pref/"));
    LITTLEFS.mkdir(String("/pref/") + s.name);
    File f = LITTLEFS.open(prefPath(s.name, key), "w");
    if (!f) return 0;
    size_t written = f.write((const uint8_t*)value, len);
    f.close();
    return written;
}

size_t IPreferences::getBytes(const char* key, void* buf, size_t maxLen) {
    auto& s = state(this);
    File f = LITTLEFS.open(prefPath(s.name, key), "r");
    if (!f) return 0;
    size_t read = f.read((uint8_t*)buf, maxLen);
    f.close();
    return read;
}

bool IPreferences::isKey(const char* key) {
    auto& s = state(this);
    return LITTLEFS.exists(prefPath(s.name, key));
}

PreferenceType IPreferences::getType(const char* key) {
    auto& s = state(this);
    File f = LITTLEFS.open(prefPath(s.name, key), "r");
    if (!f) return PT_INVALID;
    size_t sz = f.size();
    f.close();
    if (sz == 1) return PT_U8;
    if (sz == 2) return PT_U16;
    if (sz == 4) return PT_U32;
    if (sz == 8) return PT_U64;
    return PT_BLOB;
}

size_t IPreferences::putChar(const char* key, int8_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putUChar(const char* key, uint8_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putShort(const char* key, int16_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putUShort(const char* key, uint16_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putInt(const char* key, int32_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putUInt(const char* key, uint32_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putLong(const char* key, int32_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putULong(const char* key, uint32_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putLong64(const char* key, int64_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putULong64(const char* key, uint64_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putFloat(const char* key, float_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putDouble(const char* key, double_t value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putBool(const char* key, bool value) { return putBytes(key, &value, sizeof(value)); }
size_t IPreferences::putString(const char* key, const char* value) { return putBytes(key, value, strlen(value) + 1); }
size_t IPreferences::putString(const char* key, String value) { return putString(key, value.c_str()); }

int8_t IPreferences::getChar(const char* key, int8_t defaultValue) { int8_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
uint8_t IPreferences::getUChar(const char* key, uint8_t defaultValue) { uint8_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
int16_t IPreferences::getShort(const char* key, int16_t defaultValue) { int16_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
uint16_t IPreferences::getUShort(const char* key, uint16_t defaultValue) { uint16_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
int32_t IPreferences::getInt(const char* key, int32_t defaultValue) { int32_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
uint32_t IPreferences::getUInt(const char* key, uint32_t defaultValue) { uint32_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
int32_t IPreferences::getLong(const char* key, int32_t defaultValue) { return getInt(key, defaultValue); }
uint32_t IPreferences::getULong(const char* key, uint32_t defaultValue) { return getUInt(key, defaultValue); }
int64_t IPreferences::getLong64(const char* key, int64_t defaultValue) { int64_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
uint64_t IPreferences::getULong64(const char* key, uint64_t defaultValue) { uint64_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
float_t IPreferences::getFloat(const char* key, float_t defaultValue) { float_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
double_t IPreferences::getDouble(const char* key, double_t defaultValue) { double_t v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
bool IPreferences::getBool(const char* key, bool defaultValue) { bool v; return getBytes(key, &v, sizeof(v)) == sizeof(v) ? v : defaultValue; }
size_t IPreferences::getString(const char* key, char* value, size_t maxLen) { return getBytes(key, value, maxLen); }
String IPreferences::getString(const char* key, String defaultValue) {
    size_t len = getBytesLength(key);
    if (len == 0) return defaultValue;
    char* buf = new char[len];
    if (!buf) return defaultValue;
    getBytes(key, buf, len);
    String s(buf);
    delete[] buf;
    return s;
}
size_t IPreferences::getBytesLength(const char* key) {
    auto& s = state(this);
    File f = LITTLEFS.open(prefPath(s.name, key), "r");
    if (!f) return 0;
    size_t sz = f.size();
    f.close();
    return sz;
}
size_t IPreferences::freeEntries() {
    return 0;
}
