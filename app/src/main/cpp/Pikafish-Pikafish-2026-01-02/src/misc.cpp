/*
  Stockfish, a UCI chess playing engine derived from Glaurung 2.1
  Copyright (C) 2004-2026 The Stockfish developers (see AUTHORS file)

  Stockfish is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Stockfish is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

#include "misc.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <iterator>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#if defined(_WIN32) || defined(_WIN64)
    #ifndef WIN32_LEAN_AND_MEAN
        #define WIN32_LEAN_AND_MEAN
    #endif
    #include <windows.h>
#else
    #include <sys/mman.h>
    #include <sys/stat.h>
    #include <unistd.h>
#endif

#include "types.h"
#include "uci.h"

#include <android/log.h>
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Pikafish", __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Pikafish", __VA_ARGS__)

#include "external/zstd.h"

namespace Stockfish {

void (*jni_callback)(const char*) = nullptr;

void set_jni_callback(void (*callback)(const char*)) { jni_callback = callback; }

void jni_output(const std::string& msg) {
    if (jni_callback)
    {
        jni_callback(msg.c_str());
    }
}

std::stringstream& jni_oss() {
    static thread_local std::stringstream ss;
    return ss;
}

std::ostream& operator<<(std::ostream& os, SyncCout sc) {
    static std::mutex* m = new std::mutex();

    if (sc == IO_LOCK)
        m->lock();

    if (sc == IO_UNLOCK)
    {
        std::stringstream& ss = jni_oss();
        if (ss.tellp() > 0) {
            std::string s = ss.str();
            jni_output(s);
            ss.str("");
            ss.clear();
        }
        if (m) m->unlock();
    }

    return os;
}

void sync_cout_start() { jni_oss() << IO_LOCK; }
void sync_cout_end() { jni_oss() << IO_UNLOCK; }

std::string engine_info(bool uci) {
    return "Pikafish " + std::string(VERSION) + (uci ? " by the Pikafish developers" : "");
}

std::string compiler_info() {
    return "Compiled by " + std::string(COMPILER_NAME) + " " + std::string(COMPILER_VERSION);
}

std::string engine_version_info() {
    return std::string(VERSION);
}

// Trampoline helper to avoid moving Logger to misc.h
void start_logger(const std::string& /*fname*/) {
    // Logger::start(fname);
}

// split() returns a vector of strings obtained by splitting s with delimiters.
std::vector<std::string> split(std::string_view s, std::string_view delimiters) {
    std::vector<std::string> res;
    size_t                   begin, pos = 0;

    while ((begin = s.find_first_not_of(delimiters, pos)) != std::string_view::npos)
    {
        pos = s.find_first_of(delimiters, begin);
        res.emplace_back(s.substr(begin, pos - begin));
    }

    return res;
}

void remove_whitespace(std::string& s) {
    s.erase(std::remove_if(s.begin(), s.end(), [](unsigned char c) { return std::isspace(c); }),
            s.end());
}

bool is_whitespace(std::string_view s) {
    return std::all_of(s.begin(), s.end(), [](unsigned char c) { return std::isspace(c); });
}

size_t str_to_size_t(const std::string& s) {
    std::stringstream ss(s);
    size_t            res = 0;
    ss >> res;
    return res;
}

std::string read_compressed_nnue(const std::string& filename) {
    std::ifstream file(filename, std::ios::binary);
    if (!file)
    {
        ALOGE("read_compressed_nnue: Failed to open file: %s", filename.c_str());
        return "";
    }

    std::vector<char> fileData((std::istreambuf_iterator<char>(file)),
                               std::istreambuf_iterator<char>());
    file.close();

    if (fileData.empty())
    {
        ALOGE("read_compressed_nnue: File is empty: %s", filename.c_str());
        return "";
    }

    size_t fileSize = fileData.size();

    unsigned long long const decompressedSize =
      ZSTD_getFrameContentSize(fileData.data(), fileSize);

    if (decompressedSize == ZSTD_CONTENTSIZE_ERROR)
    {
        return std::string(fileData.begin(), fileData.end());
    }

    if (decompressedSize == ZSTD_CONTENTSIZE_UNKNOWN)
    {
        // 尝试判断：如果文件头看起来不像 ZSTD，直接返回原始数据
        // ZSTD Magic Number is 0xFD2FB528
        uint32_t magic = 0;
        if (fileSize >= 4) {
            memcpy(&magic, fileData.data(), 4);
        }

        if (magic != 0xFD2FB528) {
            return std::string(fileData.begin(), fileData.end());
        }

        // 如果确实是 ZSTD 但大小未知，尝试用一个足够大的缓冲区解压
        // 对于 NNUE，512MB 绝对足够了
        size_t capacity = 1024 * 1024 * 128; // 128MB
        std::string buffer;
        buffer.resize(capacity);
        size_t const actual = ZSTD_decompress(buffer.data(), capacity, fileData.data(), fileSize);
        if (ZSTD_isError(actual)) {
             ALOGE("read_compressed_nnue: Large buffer decompression failed: %s", ZSTD_getErrorName(actual));
             return "";
        }
        buffer.resize(actual);
        return buffer;
    }

    std::string decompressedData;
    decompressedData.resize(decompressedSize);

    size_t const result = ZSTD_decompress(decompressedData.data(), decompressedSize,
                                          fileData.data(), fileData.size());

    if (ZSTD_isError(result))
    {
        ALOGE("read_compressed_nnue: Decompression error: %s", ZSTD_getErrorName(result));
        return "";
    }

    return decompressedData;
}

CommandLine::CommandLine(int argc, char** argv) :
    argc(argc),
    argv(argv) {}

std::string CommandLine::get_binary_directory(const std::string& path) {
    size_t pos = path.find_last_of("\\/");
    return (pos == std::string::npos) ? "" : path.substr(0, pos);
}

}  // namespace Stockfish
