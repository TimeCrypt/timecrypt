cmake_minimum_required(VERSION 3.5)
project(fastaes)

find_package(Java REQUIRED)
find_package(JNI REQUIRED)

file(MAKE_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/out)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/out)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g -Wall -O3 -march=native")

INCLUDE_DIRECTORIES(${CMAKE_CURRENT_SOURCE_DIR}/include ${JNI_INCLUDE_DIRS})
add_library(fastaes-jni-wrapper SHARED aes-fast-jni.c aes.h)