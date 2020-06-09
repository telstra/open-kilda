include(ExternalProject)

ExternalProject_Add(zeromq_external
        URL
        https://github.com/zeromq/libzmq/releases/download/v4.3.1/zeromq-4.3.1.tar.gz
        URL_HASH
        SHA1=6cce22d830eaf95feff7cab00744df13ad7ab7f3
        INSTALL_COMMAND
        mkdir -p ${STAGED_INSTALL_PREFIX}/zeromq/include/zeromq ${STAGED_INSTALL_PREFIX}/zeromq/lib
        COMMAND cp -r -T -L <SOURCE_DIR>/include ${STAGED_INSTALL_PREFIX}/zeromq/include/zeromq
        COMMAND cp -r -T <BINARY_DIR>/lib/ ${STAGED_INSTALL_PREFIX}/zeromq/lib
        )

ExternalProject_Add(cppzmq_external
        URL https://github.com/zeromq/cppzmq/archive/v4.3.0.tar.gz
        URL_HASH SHA1=afc87286b15b035dbabcb99ef410143c3a24685c
        DEPENDS zeromq_external
        CONFIGURE_COMMAND ""
        BUILD_COMMAND ""
        INSTALL_COMMAND
        COMMAND cp <SOURCE_DIR>/zmq.hpp ${STAGED_INSTALL_PREFIX}/zeromq/include/zeromq
        COMMAND cp <SOURCE_DIR>/zmq_addon.hpp ${STAGED_INSTALL_PREFIX}/zeromq/include/zeromq
        )


add_library(zeromq INTERFACE)
target_link_libraries(zeromq INTERFACE ${STAGED_INSTALL_PREFIX}/zeromq/lib/libzmq.a)

add_dependencies(zeromq zeromq_external cppzmq_external)
target_include_directories(zeromq INTERFACE ${STAGED_INSTALL_PREFIX}/zeromq/include/)
target_include_directories(zeromq INTERFACE ${STAGED_INSTALL_PREFIX}/zeromq/include/zeromq)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)
target_link_libraries(zeromq INTERFACE Threads::Threads)
