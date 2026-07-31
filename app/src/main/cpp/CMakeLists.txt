# 3.31 or newer: earlier releases have no flag mapping for C++23 and fail to configure rather than
# compile. Which CMake AGP runs is pinned in app/build.gradle.kts.
cmake_minimum_required(VERSION 3.31)

# LANGUAGES CXX so that configuring does not go looking for a C compiler this never uses.
project(aoscoremonitor LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# One library rather than one per screen: every reading loads through the same System.loadLibrary
# call, so splitting the sources adds no second thing that can fail to load.
add_library(
    system_monitor
    SHARED
    native_util.cpp
    system_monitor.cpp
    cpu_info.cpp
    memory_map.cpp
    elf_modules.cpp
    storage_info.cpp
)

# The conversion warnings earn their place here: this code carries kernel counters — size_t,
# fsfilcnt64_t — into JSON as uint64_t, where a silent narrowing is a wrong reading rather than a
# crash.
target_compile_options(system_monitor PRIVATE
    -Wall
    -Wextra
    -Wshadow
    -Wconversion
    -Wsign-conversion
)

# The NDK links libc++ statically, and a static archive's symbols are re-exported by whatever links
# it, so without a version script this library publishes the whole standard library into the
# process's global symbol lookup. libsystem_monitor.map.txt says what it exports instead.
set(version_script "${CMAKE_CURRENT_SOURCE_DIR}/libsystem_monitor.map.txt")
target_link_options(system_monitor PRIVATE "-Wl,--version-script=${version_script}")
# Editing the script alone is a reason to relink.
set_target_properties(system_monitor PROPERTIES LINK_DEPENDS "${version_script}")

target_link_libraries(system_monitor PRIVATE log)
