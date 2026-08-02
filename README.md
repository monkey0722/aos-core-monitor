# AOS Core Monitor

Comprehensive monitoring software for deeply understanding the internal structure of AOS. Because sometimes you need to know what your Android system is secretly plotting.

Twenty-one screens, each with one subject. Everything shown is measured on the device — where a reading cannot be taken, the screen says which reading and why, rather than filling the gap with a plausible number.

## What it inspects

**The Android framework** — System Info, System Logs, Diagnostics, Security, Framework, HAL Interface, Sensors

**This process, through JNI** — Kernel Counters, CPU Cores, Threads, Memory Map, Native Libraries, Descriptors, Credentials

**What it can reach** — Storage, Network Stats, TCP Connections

**Display, frames and the GPU** — Display, Frame Pacing, Vulkan

**Audio** — Audio Path

The native screens read what the Java API cannot reach: `dl_iterate_phdr` for the linker's module list, `mallinfo2` for the native heap, `sched_getaffinity` for a thread's CPU mask, `readlink` and `fcntl` for the open descriptors, the capability masks the kernel prints only in `/proc/self/status`, and Vulkan — a C API the platform has never given Java a binding to — for the GPU's driver, memory heaps and queue families.

Audio Path is the one that asks rather than reads. It opens four output streams with different requests and reports what the system granted against what was wanted — whether the exclusive MMAP path was on offer, what burst size the stream actually settled on, and what the hardware underneath runs at, which `AAudioStream_getHardwareSampleRate` reports and nothing in the framework does. None of the streams is ever started, so nothing is played and no audio focus is taken; the cost of stopping there is the underrun count, which means nothing on a stream that never ran. They are served by one shared library, `libsystem_monitor`, built from `app/src/main/cpp`.

## Building

| | |
|---|---|
| JDK | 17 |
| minSdk / targetSdk / compileSdk | 33 / 36 / 37 |
| NDK | 29.0.14206865 |
| CMake | 3.31.6 |
| C++ | 23 |

The NDK and CMake versions are pinned rather than preferred: earlier NDKs align a library's load segments for a 4 KB page, which cannot be mapped on the 16 KB-page devices Android 15 introduced, and AGP's bundled CMake 3.22.1 has no flag mapping for C++23.

```bash
./gradlew assembleDebug          # app plus the native library, for all four ABIs
./gradlew testDebugUnitTest      # parsers and arithmetic, off-device
./gradlew connectedDebugAndroidTest   # needs a device: runs every native collector for real
./gradlew lint
```

The native build treats its warnings as errors. The conversion warnings in particular guard against a silent narrowing, which here produces a wrong reading rather than a crash.

## Code Style

This project enforces consistent code style across both Kotlin and C++ codebases.

For detailed information about code style guidelines, tools, and configuration, please see the [Code Style Guide](./docs/code-style.md).

```bash
./scripts/format.sh              # format both languages
./scripts/format.sh --check cpp  # report only, which is what CI runs
```
