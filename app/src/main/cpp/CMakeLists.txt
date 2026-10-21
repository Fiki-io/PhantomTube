cmake_minimum_required(VERSION 3.22.1)

project("phantom_core")

# Size optimization flags (nostdlib++ + Oz + LTO eliminates unused symbols and minimizes binary footprint)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Oz -flto -fno-exceptions -fno-rtti -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Oz -flto -Wl,--gc-sections -Wl,--strip-all -Wl,--icf=all -nostdlib++")

add_library(
    phantom_core
    SHARED
    phantom_core.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    phantom_core
    ${log-lib}
)
