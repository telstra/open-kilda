include(ExternalProject)

ExternalProject_Add(
        protobuf_external
        URL https://github.com/protocolbuffers/protobuf/releases/download/v3.8.0/protobuf-cpp-3.8.0.tar.gz
        URL_HASH SHA1=a2204357cc9b843b963768650aa6713a418f8e5d
        CMAKE_CACHE_ARGS
        "-DCMAKE_BUILD_TYPE:STRING=Release"
        "-Dprotobuf_BUILD_TESTS:BOOL=OFF"
        "-Dprotobuf_BUILD_EXAMPLES:BOOL=OFF"
        "-Dprotobuf_WITH_ZLIB:BOOL=OFF"
        "-DCMAKE_CXX_COMPILER:STRING=${CMAKE_CXX_COMPILER}"
        # other project specific parameters
        SOURCE_SUBDIR cmake
        INSTALL_COMMAND
        mkdir -p ${STAGED_INSTALL_PREFIX}/protobuf/include/protobuf ${STAGED_INSTALL_PREFIX}/protobuf/lib ${STAGED_INSTALL_PREFIX}/protobuf/bin
        COMMAND cp -r <SOURCE_DIR>/src/google/ ${STAGED_INSTALL_PREFIX}/protobuf/include/
        COMMAND cp <BINARY_DIR>/libprotobuf.a ${STAGED_INSTALL_PREFIX}/protobuf/lib
        COMMAND cp <BINARY_DIR>/protoc ${STAGED_INSTALL_PREFIX}/protobuf/bin
)

add_library(protobuf INTERFACE)
target_link_libraries(protobuf INTERFACE ${STAGED_INSTALL_PREFIX}/protobuf/lib/libprotobuf.a)
add_dependencies(protobuf protobuf_external)
target_include_directories(protobuf INTERFACE ${STAGED_INSTALL_PREFIX}/protobuf/include/)
