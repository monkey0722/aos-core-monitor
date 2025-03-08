# Code Style Guide

This project enforces consistent code style across both Kotlin and C++ codebases.

## Kotlin Style

This project uses [ktlint](https://github.com/JLLeitschuh/ktlint-gradle) to maintain Kotlin code style.

To check Kotlin code style:

```bash
./gradlew ktlintCheck
```

To format Kotlin code automatically:

```bash
./gradlew ktlintFormat
```

## C++ Style

This project uses [clang-format](https://clang.llvm.org/docs/ClangFormat.html) to maintain C++ code style based on the project's `.clang-format` configuration file.

To format C++ code, you need to have `clang-format` installed:

## Automatic Formatting

For convenience, a script is provided to format both Kotlin and C++ code in a single command:

```bash
# From the project root directory
./scripts/format.sh
```

The script will:

1. Format all Kotlin code using ktlint
2. Format all C++ code in app/src/main/cpp using clang-format
3. Report any files that were modified during formatting
