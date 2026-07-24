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

#ifndef MISC_H_INCLUDED
#define MISC_H_INCLUDED

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <iosfwd>
#include <string>
#include <vector>
#include <string_view>
#include <android/log.h>

#ifdef NDEBUG
    #define ALOGD(...)
    #define ALOGE(...)
#else
    #define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Pikafish", __VA_ARGS__)
    #define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Pikafish", __VA_ARGS__)
#endif

#include "types.h"

namespace Stockfish {

void set_jni_callback(void (*callback)(const char*));
void jni_output(const std::string& msg);
    std::stringstream&  jni_oss();

std::string engine_info(bool uci = false);
std::string compiler_info();
std::string engine_version_info();

void start_logger(const std::string& fname);

bool has_large_pages();

struct CommandLine {
    int    argc;
    char** argv;

    CommandLine(int argc, char** argv);
    static std::string get_binary_directory(const std::string& path);
};

typedef std::int64_t TimePoint;  // A value in milliseconds

inline TimePoint now() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

enum SyncCout {
    IO_LOCK,
    IO_UNLOCK
};
std::ostream& operator<<(std::ostream&, SyncCout);

#define sync_cout jni_oss() << IO_LOCK
#define sync_endl "\n" << IO_UNLOCK

void sync_cout_start();
void sync_cout_end();

// True if and only if the binary is compiled on a little-endian machine
static inline const std::uint16_t Le             = 1;
static inline const bool          IsLittleEndian = *reinterpret_cast<const char*>(&Le) == 1;


std::vector<std::string> split(std::string_view s, std::string_view delimiters);
void remove_whitespace(std::string& s);
bool is_whitespace(std::string_view s);
size_t str_to_size_t(const std::string& s);

// dbg_hit_on(c) returns true if condition c is true, and it also
// increments a static counter.
// dbg_print() prints the statistics of all the counters.
#ifdef DEBUG
    #define dbg_hit_on(c) Stockfish::dbg_hit_on_f(__FILE__, __LINE__, !!(c))
void dbg_print();
bool dbg_hit_on_f(const char* file, int line, bool hit);
#else
    #define dbg_hit_on(c) (!!(c))
    #define dbg_print()
#endif

void dbg_print_position(const std::string& fen, const std::string& title = "");

}  // namespace Stockfish

#endif  // #ifndef MISC_H_INCLUDED
