cmake_minimum_required(VERSION 3.5)
project(sslaes)

find_package(Java REQUIRED)
find_package(JNI REQUIRED)
find_package(OpenSSL REQUIRED)
message(${OPENSSL_VERSION})


file(MAKE_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/out)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/out)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g -Wall -O3 -march=native")

INCLUDE_DIRECTORIES(${OPENSSL_INCLUDE_DIR} ${JNI_INCLUDE_DIRS})
add_library(sslaes-jni-wrapper SHARED aes-openssl-jni.c)

target_link_libraries(sslaes-jni-wrapper ${OPENSSL_CRYPTO_LIBRARY})