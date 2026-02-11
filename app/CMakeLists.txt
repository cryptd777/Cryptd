cmake_minimum_required(VERSION 3.22.1)
project(vmapp)

set(CMAKE_CXX_STANDARD 17)

# JNI bridge
add_library(vmbridge SHARED
    src/main/cpp/vmbridge.cpp
)

# If you have prebuilt QEMU shared libs, place them under app/src/main/jniLibs/<abi>/
# and list them here as IMPORTED libraries.
# Example for libqemu.so (name depends on your build):
# add_library(qemu SHARED IMPORTED)
# set_target_properties(qemu PROPERTIES IMPORTED_LOCATION
#     ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libqemu.so)

# Linker flags required by QEMU/SDL can be added here.
# target_link_libraries(vmbridge qemu)

target_link_libraries(vmbridge
    android
    log
)
