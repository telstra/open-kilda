include(ExternalProject)

ExternalProject_Add(pcapplusplus_external
        URL
        https://github.com/seladb/PcapPlusPlus/releases/download/v19.12/pcapplusplus-19.12-source-linux.tar.gz
        URL_HASH
        SHA1=009c51d4389e9d5937ad133b73265ad176eee9c9
        DEPENDS
        dpdk_external
        CONFIGURE_COMMAND
        cd PcapPlusPlus && ./configure-linux.sh --use-immediate-mode --dpdk --dpdk-home ${DPDK_ROOT}
        PATCH_COMMAND
        cd PcapPlusPlus && patch -p1 < ${CMAKE_CURRENT_SOURCE_DIR}/dpdk-init.patch
        BUILD_COMMAND
        cd PcapPlusPlus && ${CMAKE_MAKE_PROGRAM} libs
        UPDATE_COMMAND
        ""
        INSTALL_COMMAND
        mkdir -p ${STAGED_INSTALL_PREFIX}/pcapplusplus/include/pcapplusplus ${STAGED_INSTALL_PREFIX}/pcapplusplus/lib
        COMMAND cp -r -T <SOURCE_DIR>/PcapPlusPlus/Dist/header ${STAGED_INSTALL_PREFIX}/pcapplusplus/include/pcapplusplus
        COMMAND cp <SOURCE_DIR>/PcapPlusPlus/Dist/libPcap++.a ${STAGED_INSTALL_PREFIX}/pcapplusplus/lib/
        COMMAND cp <SOURCE_DIR>/PcapPlusPlus/Dist/libPacket++.a ${STAGED_INSTALL_PREFIX}/pcapplusplus/lib/
        COMMAND cp <SOURCE_DIR>/PcapPlusPlus/Dist/libCommon++.a ${STAGED_INSTALL_PREFIX}/pcapplusplus/lib/
        BUILD_IN_SOURCE 1
        )

add_library(pcapplusplus INTERFACE)
add_dependencies(pcapplusplus pcapplusplus_external)
target_include_directories(pcapplusplus INTERFACE ${STAGED_INSTALL_PREFIX}/pcapplusplus/include/)

set(pcapplusplus_components Pcap++ Packet++ Common++)

foreach (pcapplusplus_component ${pcapplusplus_components})
    add_library(pcapplusplus_${pcapplusplus_component} INTERFACE)
    target_link_libraries(pcapplusplus_${pcapplusplus_component} INTERFACE ${STAGED_INSTALL_PREFIX}/pcapplusplus/lib/lib${pcapplusplus_component}.a)
    target_link_libraries(pcapplusplus INTERFACE pcapplusplus_${pcapplusplus_component})
    add_dependencies(pcapplusplus_${pcapplusplus_component} pcapplusplus_external)
    list(APPEND pcapplusplus_libraries "pcapplusplus_${pcapplusplus_component}")
endforeach ()
