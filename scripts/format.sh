#!/bin/bash

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

echo -e "\n${BLUE}${BOLD}========== Format Tool ==========${NC}"

# Format Kotlin code
format_kotlin() {
    echo -e "\n${YELLOW}${BOLD}[1/2] Formatting Kotlin code...${NC}"
    
    # Check if Gradle wrapper has execution permission
    if [ ! -x "./gradlew" ]; then
        chmod +x ./gradlew
    fi
    
    # Run KtLint
    if ./gradlew ktlintFormat; then
        echo -e "${GREEN}${BOLD}Kotlin formatting completed${NC}"
        return 0
    else
        echo -e "${RED}${BOLD}Error during Kotlin formatting${NC}"
        return 1
    fi
}

# Format C++ code
format_cpp() {
    echo -e "\n${YELLOW}${BOLD}[2/2] Formatting C++ code...${NC}"
    
    # Check if clang-format is installed
    if ! command -v clang-format &> /dev/null; then
        echo -e "${RED}${BOLD}clang-format is not installed${NC}"
        echo -e "${YELLOW}brew install clang-format${NC}"
        return 1
    fi
    
    # Find C++ files to format
    CPP_FILES=$(find app/src/main/cpp -type f \( -name "*.cpp" -o -name "*.h" -o -name "*.hpp" -o -name "*.c" -o -name "*.cc" \))
    
    if [ -z "$CPP_FILES" ]; then
        echo -e "${YELLOW}${BOLD}No C++ files found${NC}"
        return 0
    fi
    
    # Format C++ files
    local status=0
    for file in $CPP_FILES; do
        echo -e "  ${BLUE}•${NC} $file"
        if ! clang-format -style=file -i "$file"; then
            status=1
        fi
    done

    if [ $status -ne 0 ]; then
        echo -e "${RED}${BOLD}Error during C++ formatting${NC}"
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
    # Format Kotlin code
    format_kotlin
    KOTLIN_STATUS=$?
    
    # Format C++ code
    format_cpp
    CPP_STATUS=$?
    
    # Check for changes (if running within a Git repository)
    if git rev-parse --is-inside-work-tree &> /dev/null; then
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
