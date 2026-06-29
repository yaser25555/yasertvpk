cmake_minimum_required(VERSION 3.22.1)
project("yassertv_core")

add_library(yassertv_core SHARED
    string_encryption.c
    native_player.c
    jni_bridge.c
)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(yassertv_core PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(yassertv_core ${log-lib} ${android-lib})
