cmake_minimum_required(VERSION 3.22)
project(royerrife)

set(CMAKE_CXX_STANDARD 11)

# ncnn viene precompilado en ncnn/, con su propia configuracion de CMake. Se
# usa esa en vez de declarar las bibliotecas a mano porque ncnn arrastra
# glslang, OpenMP y pthreads, y el orden de enlazado entre ellas importa.
set(ncnn_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(royerrife SHARED
        rife_jni.cpp
        rife/rife.cpp
        rife/warp.cpp
        waifu2x_jni.cpp
        waifu2x/waifu2x.cpp)

target_include_directories(royerrife PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/include/ncnn
        ${CMAKE_CURRENT_SOURCE_DIR}/rife
        ${CMAKE_CURRENT_SOURCE_DIR}/waifu2x)

target_link_libraries(royerrife
        ncnn
        android
        log)
