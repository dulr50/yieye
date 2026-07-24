cmake_minimum_required(VERSION 3.22.1)

project("pikafish")

set(PIKAFISH_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/Pikafish-Pikafish-2026-01-02)
set(PIKAFISH_SRC ${PIKAFISH_ROOT}/src)

# 1. 模拟 Makefile 的递归搜寻源码逻辑 (SRCS = $(shell find . -name '*.cpp'))
file(GLOB_RECURSE PIKAFISH_SOURCES
    "${PIKAFISH_SRC}/*.cpp"
    "${PIKAFISH_SRC}/*.S"
    "${PIKAFISH_SRC}/*.c"
    "${PIKAFISH_SRC}/external/common/*.cpp"
    "${PIKAFISH_SRC}/external/decompress/*.cpp"
)

add_library(pikafish SHARED
    pikafish_jni.cpp
    ${PIKAFISH_SOURCES}
)

# 2. 还原 Makefile 的包含路径和 VPATH
target_include_directories(pikafish PRIVATE
    ${PIKAFISH_SRC}
    ${PIKAFISH_SRC}/external
    ${PIKAFISH_SRC}/nnue
    ${PIKAFISH_SRC}/nnue/features
    ${PIKAFISH_SRC}/nnue/layers
)

# 3. 还原 Makefile 的编译宏配置 (对应 armv8 架构)
target_compile_definitions(pikafish PRIVATE
    VERSION="2026-01-02"
    COMPILER_NAME="Clang"
    COMPILER_VERSION="14.0.0"
    IS_64BIT
    USE_NNUE
    USE_NEON        # Android aarch64 默认支持
    USE_POPCNT      # Android aarch64 默认支持
    NDEBUG          # 对应 make profile-build
    ZSTD_MULTITHREAD
)

# 4. 优化选项
target_compile_options(pikafish PRIVATE
    -O3
    -fno-exceptions
    -std=c++20
    -ffast-math
    -flto           # 开启 LTO 提升性能，对应 profile-build 的一部分
)

find_library(log-lib log)
find_library(z-lib z) # 系统 zlib

target_link_libraries(pikafish
    ${log-lib}
    ${z-lib}
    -Wl,--gc-sections
)
