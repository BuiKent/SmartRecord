cmake_minimum_required(VERSION 3.10)

project(whisper)

cmake_policy(SET CMP0079 NEW)
set(CMAKE_CXX_STANDARD 17)

# Path to whisper.cpp (adjust based on your directory structure)
# From app/src/main/cpp/CMakeLists.txt, go up to project root, then to parent, then to whisper.cpp
# app/src/main/cpp -> ../../../ -> project root -> ../ -> AndroidStudioProjects -> whisper.cpp
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../../whisper.cpp)

if(NOT EXISTS "${WHISPER_LIB_DIR}")
    message(FATAL_ERROR "Whisper.cpp directory not found at: ${WHISPER_LIB_DIR}")
endif()

message(STATUS "Whisper.cpp directory: ${WHISPER_LIB_DIR}")

# Source files
set(SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

# Build library
add_library(whisper SHARED ${SOURCE_FILES})

target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.5.4")

# Optimizations
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper PRIVATE -O3)
else()
    target_compile_options(whisper PRIVATE -O0 -g)
endif()

# Disable OpenMP for Android
set(GGML_OPENMP OFF CACHE BOOL "ggml: use OpenMP" FORCE)

include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

# Link libraries
target_link_libraries(whisper 
    ${LOG_LIB} 
    android 
    ggml
    c++_shared
)

# Include directories
include_directories(${WHISPER_LIB_DIR})
include_directories(${WHISPER_LIB_DIR}/src)
include_directories(${WHISPER_LIB_DIR}/include)
include_directories(${WHISPER_LIB_DIR}/ggml/include)
include_directories(${WHISPER_LIB_DIR}/ggml/src)

