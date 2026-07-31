cmake_minimum_required(VERSION 3.22.1)

# Set project name
project(aoscoremonitor)

# Set C++ standard to C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Create shared library (.so)
#
# One library rather than one per screen: every reading is loaded through the same
# System.loadLibrary call on the Kotlin side, so splitting the sources keeps each collector
# readable without adding a second thing that can fail to load.
add_library(
    # Library name
    system_monitor
    # SHARED: specify the shared library type
    SHARED
    # Source files
    native_util.cpp
    system_monitor.cpp
    cpu_info.cpp
    memory_map.cpp
    elf_modules.cpp
    storage_info.cpp
)

target_compile_options(system_monitor PRIVATE -Wall -Wextra -Wno-unused-parameter)

# Align the load segments for a 16 KB page.
#
# Android 15 introduced devices — and emulator images — whose pages are 16 KB rather than 4 KB.
# A library laid out for 4 KB pages cannot be mapped there, and the failure is not obvious from
# the message the linker gives: it maps the segments at the wrong offsets and then reports the
# symbol hash table as "empty/missing DT_HASH/DT_GNU_HASH". Every native reading in the app was
# silently unavailable on such a device, with the screens falling back to sample data.
#
# NDK r28 makes this the default; this project builds against r27, which does not.
target_link_options(system_monitor PRIVATE "-Wl,-z,max-page-size=16384")

# Find Android log library
find_library(
    log-lib
    log
)

# Link libraries
target_link_libraries(
    system_monitor
    ${log-lib}
)
