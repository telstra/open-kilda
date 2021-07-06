sed -i "s/CONFIG_RTE_PORT_PCAP=n/CONFIG_RTE_PORT_PCAP=y/g" build/.config
sed -i "s/CONFIG_RTE_LIBRTE_PMD_PCAP=n/CONFIG_RTE_LIBRTE_PMD_PCAP=y/g" build/.config
sed -i "s/CONFIG_RTE_LIBEAL_USE_HPET=n/CONFIG_RTE_LIBEAL_USE_HPET=y/g" build/.config
patch -p0 < kni.patch
patch -p0 < eal_rte_random.patch
