cmake_minimum_required(VERSION 3.22.1)
project(jorcam_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp se descarga en esta carpeta durante la compilación (ver .github/workflows/build-android.yml)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp whisper_build)

add_library(jorcam_whisper SHARED whisper_jni.cpp)
target_include_directories(jorcam_whisper PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/ggml/include)
target_link_libraries(jorcam_whisper whisper log)
