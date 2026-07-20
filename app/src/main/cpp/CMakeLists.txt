cmake_minimum_required(VERSION 3.22.1)
project(smartxray)

add_library(smartxray SHARED smartxray_jni.cpp)
find_library(log-lib log)

# Support 16 KB page sizes for Android 15+
target_link_options(smartxray PUBLIC "-Wl,-z,max-page-size=16384")

target_link_libraries(smartxray ${log-lib})
