#pragma once
#include <Arduino.h>
#include <lt_logger.h>

class FileBrowser {
public:
    static String listDir(const String& path);
    static String readFile(const String& path, size_t offset, size_t limit, bool encode = false);
    static String fileInfo(const String& path);
    static String deleteItem(const String& path);
    static String fsInfo();
};
