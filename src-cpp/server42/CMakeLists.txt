cmake_minimum_required(VERSION 3.15.3 FATAL_ERROR)

project(server42 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif ()

message(STATUS "Build type set to ${CMAKE_BUILD_TYPE}")

# https://gcc.gnu.org/onlinedocs/gcc/Option-Summary.html
# https://wiki.gentoo.org/wiki/GCC_optimization
if(CMAKE_BUILD_TYPE MATCHES Debug)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O0 -march=native -Wall -Wextra -Wno-unused-parameter -ggdb -msse -msse2 -msse3 -msse4.1")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=native -Wall -Wextra -Wno-unused-parameter -g -ggdb -msse -msse2 -msse3 -msse4.1")
endif()

message(STATUS "CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS}")


set_property(DIRECTORY PROPERTY EP_BASE ${CMAKE_BINARY_DIR}/subprojects)

set(STAGED_INSTALL_PREFIX ${CMAKE_BINARY_DIR}/stage)
message(STATUS "${PROJECT_NAME} staged install: ${STAGED_INSTALL_PREFIX}")

list(APPEND BOOST_COMPONENTS_REQUIRED filesystem test_exec_monitor stacktrace_backtrace system atomic log program_options context)
set(Boost_MINIMUM_REQUIRED 1.71.0)

add_subdirectory(external/upstream)

# TODO: move to superbuild pattern after clion support it
# https://github.com/dev-cafe/cmake-cookbook/tree/v1.0/chapter-08/recipe-02
find_package(Boost
        ${Boost_MINIMUM_REQUIRED}
        COMPONENTS
        ${BOOST_COMPONENTS_REQUIRED}
        )

find_library(numa numa)
find_library(pcap pcap)

function(GENERATE_PROTO_BUF PROTOFILE)
    add_custom_command(
            OUTPUT ${CMAKE_SOURCE_DIR}/src/${PROTOFILE}.pb.cc ${CMAKE_SOURCE_DIR}/src/${PROTOFILE}.pb.h
            MAIN_DEPENDENCY ${CMAKE_SOURCE_DIR}/src/${PROTOFILE}.proto
            COMMAND ${STAGED_INSTALL_PREFIX}/protobuf/bin/protoc -I=${CMAKE_SOURCE_DIR}/src/ --cpp_out=${CMAKE_SOURCE_DIR}/src/ ${CMAKE_SOURCE_DIR}/src/${PROTOFILE}.proto
            DEPENDS protobuf
    )
endfunction()

generate_proto_buf(control)
generate_proto_buf(statistics)

add_compile_options(-DBOOST_STACKTRACE_USE_BACKTRACE)
add_compile_options(-DBOOST_STACKTRACE_LINK)
find_package(Backtrace REQUIRED)

if (Backtrace_FOUND)
    if (NOT Backtrace_LIBRARIES)
        message (STATUS "Fix Backtrace_LIBRARIES")
        set(Backtrace_LIBRARIES -lbacktrace)
    endif ()
    message(STATUS "Backtrace Backtrace_HEADER '${Backtrace_HEADER}', Backtrace_INCLUDE_DIRS '${Backtrace_INCLUDE_DIRS}', Backtrace_LIBRARIES '${Backtrace_LIBRARIES}', Backtrace_LIBRARY '${Backtrace_LIBRARY}', Backtrace_INCLUDE_DIR '${Backtrace_INCLUDE_DIR}'")
else ()
    message(STATUS "Backtrace Not Found")
endif ()


add_executable(server42
        src/server42.cpp
        src/control.pb.cc
        src/statistics.pb.cc
        src/Config.cpp
        src/Control.cpp
        src/PacketGenerator.cpp
        src/DpdkCoreThread.cpp
        src/Workers.cpp
        src/DumpStats.cpp
        )

add_dependencies(server42
        boost_external
        )

target_link_libraries(server42
        PRIVATE
        -rdynamic
        pcapplusplus
        dpdk_headers
        zeromq
        protobuf
        zeromq
        Boost::headers
        Boost::filesystem
        Boost::log
        Boost::program_options
        Boost::stacktrace_backtrace
        Boost::context
        -Wl,--whole-archive ${DPDK_LIBRARIES} -Wl,--no-whole-archive
        numa
        pcap
        ${CMAKE_DL_LIBS}
        ${Backtrace_LIBRARIES}
        )

set_target_properties(server42 PROPERTIES COMPILE_FLAGS ${CMAKE_CXX_FLAGS})

add_executable(server42_test
        src/FlowPoolTest.cpp
        src/control.pb.cc
        src/statistics.pb.cc)

target_link_libraries(server42_test
        PRIVATE
        pcapplusplus
        dpdk_headers
        zeromq
        protobuf
        zeromq
        Boost::headers
        Boost::filesystem
        Boost::test_exec_monitor
        ${Backtrace_LIBRARIES}
        -Wl,--whole-archive ${DPDK_LIBRARIES} -Wl,--no-whole-archive
        numa
        pcap
        ${CMAKE_DL_LIBS}
        )

set_target_properties(server42_test PROPERTIES COMPILE_FLAGS ${CMAKE_CXX_FLAGS})

