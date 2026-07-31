cmake_minimum_required(VERSION 3.22.1)

# Set project name
project(aoscoremonitor)

# Set C++ standard to C++23
#
# NDK r29 ships Clang 21, whose C++23 support is complete enough for everything here — the
# collectors use string_view::contains, std::to_underlying and the starts_with/ends_with members
# that replaced three hand-written helpers.
set(CMAKE_CXX_STANDARD 23)
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
