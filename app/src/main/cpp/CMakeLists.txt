cmake_minimum_required(VERSION 3.22.1)

# Set project name
project(aoscoremonitor)

# Set C++ standard to C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Create shared library (.so)
add_library(
    # Library name
    system_monitor
    # SHARED: specify the shared library type
    SHARED
    # Source files
    system_monitor.cpp
)

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
