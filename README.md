# AOS Core Monitor

Comprehensive monitoring software for deeply understanding the internal structure of AOS. Because sometimes you need to know what your Android system is secretly plotting.

Twenty-one screens, each with one subject. Device readings are kept distinct from built-in sample data: when the app cannot reach a source and shows an example instead, the screen labels it as sample data and says which source was unavailable.

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

The NDK and CMake versions are pinned so local and CI builds use the same native toolchain. NDK r28 and newer produce 16 KB-aligned native libraries by default; this project uses r29. CMake 3.20 and newer support C++23, and the project currently declares and pins 3.31.6.

```bash
./gradlew assembleDebug          # app plus the native library, for all four ABIs
./gradlew testDebugUnitTest      # parsers and arithmetic, off-device
./gradlew connectedDebugAndroidTest   # needs a device: instrumented UI and JNI collector tests
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
