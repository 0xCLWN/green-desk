cmake_minimum_required(VERSION 3.22.1)
project(hev-socks5-tunnel C ASM)

set(HEV_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/hev-socks5-tunnel")

# ---- libyaml ----
file(GLOB_RECURSE YAML_SOURCES "${HEV_ROOT}/third-part/yaml/src/*.c")
add_library(libyaml STATIC ${YAML_SOURCES})
target_include_directories(libyaml PRIVATE "${HEV_ROOT}/third-part/yaml/src")
target_compile_definitions(libyaml PRIVATE
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    "YAML_VERSION_STRING=\"0.2.5\""
)

# ---- liblwip ----
file(GLOB_RECURSE LWIP_SOURCES "${HEV_ROOT}/third-part/lwip/src/*.c")
add_library(liblwip STATIC ${LWIP_SOURCES})
target_include_directories(liblwip PRIVATE
    "${HEV_ROOT}/third-part/lwip/src/include"
    "${HEV_ROOT}/third-part/lwip/src/ports/include"
)
target_compile_definitions(liblwip PRIVATE FD_SET_DEFINED SOCKLEN_T_DEFINED)

# ---- libhev-task-system ----
file(GLOB_RECURSE TASK_C_SOURCES "${HEV_ROOT}/third-part/hev-task-system/src/*.c")
# Only the dispatch .S (preprocessed): it #includes the correct arch .s via __aarch64__ etc.
# The individual arch/*.s files must NOT be compiled directly.
set(TASK_ASM_SOURCES
    "${HEV_ROOT}/third-part/hev-task-system/src/kern/task/hev-task-execute.S"
)
add_library(libhev-task-system STATIC ${TASK_C_SOURCES} ${TASK_ASM_SOURCES})
target_include_directories(libhev-task-system PRIVATE
    "${HEV_ROOT}/third-part/hev-task-system/src"
    "${HEV_ROOT}/third-part/hev-task-system/include"
)
target_compile_definitions(libhev-task-system PRIVATE
    ENABLE_STACK_OVERFLOW_DETECTION
    ENABLE_MEMALLOC_SLICE
    ENABLE_IO_SPLICE_SYSCALL
    CONFIG_STACK_BACKEND=STACK_MMAP
    CONFIG_STACK_OVERFLOW_DETECTION=1
    CONFIG_MEMALLOC_SLICE_ALIGN=16
    CONFIG_MEMALLOC_SLICE_MAX_SIZE=4096
    CONFIG_MEMALLOC_SLICE_MAX_COUNT=1000
    CONFIG_SCHED_CLOCK=CLOCK_NONE
)

# ---- hev-socks5-tunnel (shared library) ----
file(GLOB_RECURSE HEV_SOURCES "${HEV_ROOT}/src/*.c")
add_library(hev-socks5-tunnel SHARED ${HEV_SOURCES})
target_include_directories(hev-socks5-tunnel PRIVATE
    "${HEV_ROOT}/src"
    "${HEV_ROOT}/src/misc"
    "${HEV_ROOT}/src/core/include"
    "${HEV_ROOT}/third-part/yaml/include"
    "${HEV_ROOT}/third-part/lwip/src/include"
    "${HEV_ROOT}/third-part/lwip/src/ports/include"
    "${HEV_ROOT}/third-part/hev-task-system/include"
)
target_compile_definitions(hev-socks5-tunnel PRIVATE
    ANDROID
    FD_SET_DEFINED
    SOCKLEN_T_DEFINED
    ENABLE_LIBRARY
    "COMMIT_ID=\"local\""
    PKGNAME=com/green/android
    CLSNAME=TProxyService
)
target_link_libraries(hev-socks5-tunnel PRIVATE
    libyaml liblwip libhev-task-system android log
)
target_link_options(hev-socks5-tunnel PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
