cmake_minimum_required(VERSION 3.22.1)
project(aivoice C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

if (NOT EXISTS ${WHISPER_ROOT}/CMakeLists.txt)
    message(FATAL_ERROR
        "third_party/whisper.cpp is empty. Run:\n"
        "  git submodule update --init --recursive")
endif()

# Trim whisper.cpp down to the static library we actually link into the JNI
# shared object; everything else it can build is host tooling.
set(BUILD_SHARED_LIBS       OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV    OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS    OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP             OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE          OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL         OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS   OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE             OFF CACHE BOOL "" FORCE)

# Baseline for every 64-bit phone from ~2018 onwards (Cortex-A75 and later).
# fp16 arithmetic and the dot-product instructions are what make the quantised
# matmuls fast enough for interactive dictation.
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)

add_subdirectory(${WHISPER_ROOT} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp EXCLUDE_FROM_ALL)

add_library(aivoice SHARED whisper_jni.cpp)

target_link_libraries(aivoice PRIVATE whisper android log)

target_compile_options(aivoice PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_options(aivoice PRIVATE -Wl,--gc-sections)

# Android 15 ships 16 KiB memory pages on some devices; align the ELF segments
# so the loader accepts the library there as well.
target_link_options(aivoice PRIVATE -Wl,-z,max-page-size=16384)
