#!/bin/bash

# Formats both languages, or reports without changing anything.
#
#   ./scripts/format.sh                 format Kotlin and C++
#   ./scripts/format.sh --check cpp     report only, C++ alone (what CI runs)
#   ./scripts/format.sh --check kotlin  report only, Kotlin alone
#
# CI runs this rather than a command of its own. The C++ job used to carry its own copy of the file
# list, which drifted: it had no *.cpp in it, so every implementation file went unchecked while the
# one header passed, and the job reported success either way.

# Color definitions
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Note: 'set -e' is intentionally not used. Each formatter reports its own
# status so that a failure in one still lets the other run, and so the summary
# at the end is always printed.

# The one list of what counts as C or C++ here.
CPP_NAME_FILTER=(-name "*.c" -o -name "*.cc" -o -name "*.cpp" -o -name "*.cxx"
                 -o -name "*.h" -o -name "*.hh" -o -name "*.hpp")

# Overridable so CI can name the exact build it pins; clang-format's output differs between major
# versions, and a check that runs a different one than the developer did is a check that fails for
# no reason.
CLANG_FORMAT="${CLANG_FORMAT:-clang-format}"

CHECK_ONLY=0
TARGET="all"
for arg in "$@"; do
    case "$arg" in
        --check) CHECK_ONLY=1 ;;
        cpp|kotlin) TARGET="$arg" ;;
        *)
            echo "usage: $0 [--check] [cpp|kotlin]" >&2
            exit 2
            ;;
    esac
done

if [ $CHECK_ONLY -eq 1 ]; then
    echo -e "\n${BLUE}${BOLD}========== Format Check ==========${NC}"
else
    echo -e "\n${BLUE}${BOLD}========== Format Tool ==========${NC}"
fi

# Format Kotlin code
format_kotlin() {
    if [ $CHECK_ONLY -eq 1 ]; then
        echo -e "\n${YELLOW}${BOLD}Checking Kotlin code...${NC}"
    else
        echo -e "\n${YELLOW}${BOLD}Formatting Kotlin code...${NC}"
    fi

    # Check if Gradle wrapper has execution permission
    if [ ! -x "./gradlew" ]; then
        chmod +x ./gradlew
    fi

    local task="ktlintFormat"
    if [ $CHECK_ONLY -eq 1 ]; then
        task="ktlintCheck"
    fi

    # Run KtLint
    if ./gradlew "$task"; then
        echo -e "${GREEN}${BOLD}Kotlin ${task} completed${NC}"
        return 0
    else
        echo -e "${RED}${BOLD}Error during Kotlin ${task}${NC}"
        return 1
    fi
}

# Format C++ code
format_cpp() {
    if [ $CHECK_ONLY -eq 1 ]; then
        echo -e "\n${YELLOW}${BOLD}Checking C++ code...${NC}"
    else
        echo -e "\n${YELLOW}${BOLD}Formatting C++ code...${NC}"
    fi

    # Check if clang-format is installed
    if ! command -v "$CLANG_FORMAT" &> /dev/null; then
        echo -e "${RED}${BOLD}${CLANG_FORMAT} is not installed${NC}"
        echo -e "${YELLOW}brew install clang-format${NC}"
        return 1
    fi

    # Find C++ files to format
    CPP_FILES=$(find app/src/main/cpp -type f \( "${CPP_NAME_FILTER[@]}" \))

    if [ -z "$CPP_FILES" ]; then
        echo -e "${YELLOW}${BOLD}No C++ files found${NC}"
        return 0
    fi

    local status=0
    if [ $CHECK_ONLY -eq 1 ]; then
        # --Werror so that a file needing formatting is a failure rather than a note.
        if ! echo "$CPP_FILES" | xargs "$CLANG_FORMAT" --dry-run --Werror --style=file; then
            status=1
        fi
    else
        for file in $CPP_FILES; do
            echo -e "  ${BLUE}•${NC} $file"
            if ! "$CLANG_FORMAT" -style=file -i "$file"; then
                status=1
            fi
        done
    fi

    if [ $status -ne 0 ]; then
        echo -e "${RED}${BOLD}C++ formatting is not clean${NC}"
        return 1
    fi

    echo -e "${GREEN}${BOLD}C++ formatting completed${NC}"
    return 0
}

# Check for changes
check_changes() {
    echo -e "\n${YELLOW}${BOLD}Detecting and reporting changes...${NC}"

    # Check Git changes, staged and unstaged alike
    if git diff --quiet HEAD; then
        echo -e "${GREEN}${BOLD}No uncommitted changes${NC}"
    else
        echo -e "${YELLOW}${BOLD}The following files have uncommitted changes:${NC}"
        # Use echo -e per line: sed would emit the color escapes literally
        git diff --name-only HEAD | while read -r changed_file; do
            echo -e "  ${BLUE}•${NC} ${changed_file}"
        done
    fi
}

# Main process
main() {
    KOTLIN_STATUS=0
    CPP_STATUS=0

    if [ "$TARGET" = "all" ] || [ "$TARGET" = "kotlin" ]; then
        format_kotlin
        KOTLIN_STATUS=$?
    fi

    if [ "$TARGET" = "all" ] || [ "$TARGET" = "cpp" ]; then
        format_cpp
        CPP_STATUS=$?
    fi

    # Nothing was written in check mode, so there is nothing to report as changed.
    if [ $CHECK_ONLY -eq 0 ] && git rev-parse --is-inside-work-tree &> /dev/null; then
        check_changes
    fi

    # Display final result
    echo -e "\n${BLUE}${BOLD}=================================================${NC}"

    if [ $KOTLIN_STATUS -eq 0 ] && [ $CPP_STATUS -eq 0 ]; then
        echo -e "${GREEN}${BOLD}All formatting tasks completed successfully${NC}"
    else
        echo -e "${RED}${BOLD}Some formatting tasks failed${NC}"
    fi

    echo -e "${BLUE}${BOLD}=================================================${NC}"

    # Return non-zero exit code if any component failed
    if [ $KOTLIN_STATUS -ne 0 ] || [ $CPP_STATUS -ne 0 ]; then
        exit 1
    fi
}

main
