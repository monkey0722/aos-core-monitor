# 3.31 is the floor because of C++23: CMake releases that predate Clang 17 have no flag mapping
# for the dialect and fail to configure rather than compile. AGP is told which CMake to use in
# app/build.gradle.kts; this makes the requirement fail loudly if it is ever pointed at an older one.
cmake_minimum_required(VERSION 3.31)

# LANGUAGES CXX so that configuring does not go looking for a C compiler it will never use.
project(aoscoremonitor LANGUAGES CXX)

# NDK r29 ships Clang 21, whose C++23 support is complete enough for everything here — the
# collectors use string_view::contains, std::to_underlying and the starts_with/ends_with members
# that replaced three hand-written helpers.
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# One library rather than one per screen: every reading is loaded through the same
# System.loadLibrary call on the Kotlin side, so splitting the sources keeps each collector
# readable without adding a second thing that can fail to load.
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

# The conversion warnings are on because this code lives on the boundary between the kernel's
# types and JSON's: /proc counters arrive as size_t or fsfilcnt64_t and leave as uint64_t, and a
# silent narrowing there is a wrong reading rather than a crash. All of them are clean today.
target_compile_options(system_monitor PRIVATE
    -Wall
    -Wextra
    -Wshadow
    -Wconversion
    -Wsign-conversion
)

# Export only the JNI entry points.
#
# The library published 2,067 dynamic symbols where the runtime calls 10. Nearly all of them came
# from libc++, which the NDK links statically by default: a static archive's symbols are exported
# by whatever links it, so this library was re-publishing the whole standard library. That is a
# correctness problem before it is a size one — those symbols sit in the process's global lookup
# order, where they can bind another library's std:: calls to this copy of libc++.
#
# -fvisibility=hidden covers only the sources compiled here; --exclude-libs is what hides the
# symbols that arrive from the static archives. --gc-sections then drops what nothing reaches.
#
# Measured on the release build, stripped: arm64 908,888 -> 450,088 bytes, armeabi-v7a 586,248 ->
# 276,892, x86 843,980 -> 450,368, x86_64 894,040 -> 450,472 — about 1.6 MB across the four ABIs,
# and 2,067 exported symbols down to exactly the 10 JNI entry points.
#
# Two further options were measured on top of this and are deliberately absent: thin LTO grew the
# library to 481,320 bytes, and -fno-rtti changed nothing at all — this code has no dynamic_cast
# or typeid for it to remove.
target_compile_options(system_monitor PRIVATE
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
)
target_link_options(system_monitor PRIVATE "-Wl,--gc-sections" "-Wl,--exclude-libs,ALL")

# liblog comes from the NDK sysroot, so it needs no find_library lookup — naming it is enough.
target_link_libraries(system_monitor PRIVATE log)
