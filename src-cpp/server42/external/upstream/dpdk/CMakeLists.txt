include(ExternalProject)
ExternalProject_Add(dpdk_external
        URL
        http://fast.dpdk.org/rel/dpdk-19.08.2.tar.xz
        URL_HASH
        SHA1=e203bbe12aeebb638c1eb320c4abeb423b3e7aec
        DOWNLOAD_NO_PROGRESS
        1
        CONFIGURE_COMMAND
        ${CMAKE_MAKE_PROGRAM} config T=x86_64-native-linuxapp-gcc
        COMMAND cp ${CMAKE_CURRENT_SOURCE_DIR}/dpdk_config.sh <SOURCE_DIR>
        COMMAND /bin/sh dpdk_config.sh
        BUILD_COMMAND
        ${CMAKE_MAKE_PROGRAM}
        INSTALL_COMMAND
        mkdir -p ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk ${STAGED_INSTALL_PREFIX}/dpdk/lib
        COMMAND cp -r -T -L <SOURCE_DIR>/build/include ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk/
        COMMAND cp -r -T <SOURCE_DIR>/build/lib/ ${STAGED_INSTALL_PREFIX}/dpdk/lib
        BUILD_IN_SOURCE
        1
        )

add_library(dpdk_headers INTERFACE)
# SYSTEM is here for skip warns from dpdk in c++ code
# see also https://cmake.org/cmake/help/v3.0/command/include_directories.html
target_include_directories(dpdk_headers SYSTEM INTERFACE ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk)

set(dpdk_components
        pmd_bond pmd_vmxnet3_uio pmd_virtio pmd_enic pmd_i40e pmd_fm10k pmd_ixgbe net pmd_e1000 pmd_ring pmd_af_packet
        ethdev eal mbuf mempool ring kvargs hash cmdline pci bus_pci bus_vdev mempool_ring pmd_pcap
        pmd_bnxt
        )

foreach (dpdk_component ${dpdk_components})
    add_library(dpdk_${dpdk_component} INTERFACE)
    target_link_libraries(dpdk_${dpdk_component} INTERFACE ${STAGED_INSTALL_PREFIX}/dpdk/lib/librte_${dpdk_component}.a)
    add_dependencies(dpdk_${dpdk_component} dpdk_external)
    list(APPEND dpdk_raw_libraries ${STAGED_INSTALL_PREFIX}/dpdk/lib/librte_${dpdk_component}.a)
endforeach ()

set(DPDK_LIBRARIES
        ${dpdk_raw_libraries}
        CACHE PATH "dpdk libraries"
        FORCE
        )

ExternalProject_Get_property(dpdk_external SOURCE_DIR)
set(
        DPDK_ROOT ${SOURCE_DIR}
        CACHE PATH "dpdk build directory"
        FORCE
)
unset(SOURCE_DIR)


