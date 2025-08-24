include(ExternalProject)
ExternalProject_Add(dpdk_external
        URL
        http://fast.dpdk.org/rel/dpdk-20.11.3.tar.xz
        URL_HASH
        MD5=e2f9ac261eb639c92811d205926253d1
        DOWNLOAD_NO_PROGRESS
        1
        CONFIGURE_COMMAND
#        ${CMAKE_MAKE_PROGRAM} config T=x86_64-native-linuxapp-gcc
#        COMMAND cp ${CMAKE_CURRENT_SOURCE_DIR}/dpdk_config.sh <SOURCE_DIR>
#        COMMAND cp ${CMAKE_CURRENT_SOURCE_DIR}/kni.patch <SOURCE_DIR>
#        COMMAND cp ${CMAKE_CURRENT_SOURCE_DIR}/eal_rte_random.patch <SOURCE_DIR>
#        COMMAND /bin/sh dpdk_config.sh
        #COMMAND DESTDIR=${STAGED_INSTALL_PREFIX}/dpdk/tmp meson setup build
        COMMAND rm -rf build
        COMMAND DESTDIR=${STAGED_INSTALL_PREFIX}/dpdk/tmp meson setup -D use_hpet=true -D tests=false -D platform=native $<IF:$<CONFIG:Debug>,--buildtype=debug,--buildtype=release> --libdir=lib build
        BUILD_COMMAND
        ninja -C build
        INSTALL_COMMAND
        DESTDIR=${STAGED_INSTALL_PREFIX}/dpdk/tmp ninja -C build install
        COMMAND mkdir -p ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk ${STAGED_INSTALL_PREFIX}/dpdk/lib
        COMMAND cp -r -T -L ${STAGED_INSTALL_PREFIX}/dpdk/tmp/usr/local/include ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk/
        COMMAND cp -r -T -L ${STAGED_INSTALL_PREFIX}/dpdk/tmp/usr/local/lib ${STAGED_INSTALL_PREFIX}/dpdk/lib
        #COMMAND rm -rf ${STAGED_INSTALL_PREFIX}/dpdk/tmp/
        #COMMAND echo <SOURCE_DIR>/build/include
        #COMMAND echo /home/nm/p/kilda/open-kilda/src-cpp/server42/cmake-build-debug/subprojects/Source/dpdk_external/build/include
        #COMMAND echo ln -s -t <SOURCE_DIR>/build/include ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk/
        #COMMAND mkdir -p ${CMAKE_CURRENT_SOURCE_DIR}/build/include
        #COMMAND mkdir -p <SOURCE_DIR>/build/include
        COMMAND ln -s -T ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk/ <SOURCE_DIR>/build/include
        #COMMAND echo ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk/ ${CMAKE_CURRENT_SOURCE_DIR}/build/include
        BUILD_IN_SOURCE
        1
        )

add_library(dpdk_headers INTERFACE)
# SYSTEM is here for skip warns from dpdk in c++ code
# see also https://cmake.org/cmake/help/v3.0/command/include_directories.html
target_include_directories(dpdk_headers SYSTEM INTERFACE ${STAGED_INSTALL_PREFIX}/dpdk/include/dpdk)

#set(dpdk_components
#        pmd_bond pmd_vmxnet3_uio pmd_virtio pmd_enic pmd_i40e pmd_fm10k pmd_ixgbe net pmd_e1000 pmd_ring pmd_af_packet
#        ethdev eal mbuf mempool ring kvargs hash cmdline pci bus_pci bus_vdev mempool_ring pmd_pcap
#        pmd_bnxt
#        )

#        pmd_bond pmd_vmxnet3_uio pmd_virtio pmd_enic pmd_i40e pmd_fm10k pmd_ixgbe pmd_e1000 pmd_af_packet pmd_bnxt
#
#

set(dpdk_components
        eal mbuf mempool ring ethdev mempool_ring kvargs telemetry net net_pcap bus_vdev net_i40e net_virtio pci bus_pci
        hash rcu
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


