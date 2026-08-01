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

## Checking without changing anything

The same script reports rather than rewrites when given `--check`, optionally for one language:

```bash
./scripts/format.sh --check          # both
./scripts/format.sh --check cpp      # what the C++ CI job runs
./scripts/format.sh --check kotlin
```

CI runs the script rather than a command of its own, so the list of files that count as C++ is
defined once. It used to be duplicated in the workflow, where it was missing `*.cpp` — the entire
implementation went unchecked while the one header passed.

`clang-format` output differs between major versions, so CI pins `clang-format-18` through the
`CLANG_FORMAT` environment variable the script reads. Set the same variable locally to check
against exactly what CI will run:

```bash
CLANG_FORMAT=clang-format-18 ./scripts/format.sh --check cpp
```
