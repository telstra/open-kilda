#include "DumpStats.h"

#include <fstream>
#include <iomanip>

void org::openkilda::save_stats_to_file(const org::openkilda::Config::ptr &config, SharedContext &ctx) {

    std::ofstream file;
    file.open ("/tmp/server42-status.txt", std::ios::out | std::ios::trunc);
    file.setf(std::ios::left);

    constexpr uint first_column_width = 33;

    file << std::setw(first_column_width) << "device.id: " << ctx.primary_device->getDeviceId() << std::endl;
    file << std::setw(first_column_width) << "device.mac: " << ctx.primary_device->getMacAddress().toString() << std::endl;
    file << std::setw(first_column_width) << "device.pmd: " << ctx.primary_device->getPMDName() << std::endl;

    pcpp::DpdkDevice::DpdkDeviceStats stats{};

    ctx.primary_device->getStatistics(stats);

    file << std::setw(first_column_width) << "device.timestamp: " << stats.timestamp.tv_sec << std::endl;
    file << std::setw(first_column_width) << "device.rx.packets.total: " << stats.aggregatedRxStats.packets << std::endl;
    file << std::setw(first_column_width) << "device.rx.packets.per/sec: " << stats.aggregatedRxStats.packetsPerSec << std::endl;
    file << std::setw(first_column_width) << "device.rx.bytes.total: " << stats.aggregatedRxStats.bytes << std::endl;
    file << std::setw(first_column_width) << "device.rx.bytes.per/sec: " << stats.aggregatedRxStats.bytesPerSec << std::endl;
    file << std::setw(first_column_width) << "device.rx.erroneous-packets: " << stats.rxErroneousPackets << std::endl;
    file << std::setw(first_column_width) << "device.rx.mbuf-aloc-failed: " << stats.rxMbufAlocFailed << std::endl;
    file << std::setw(first_column_width) << "device.rx.packets-dropped-by-hw: " << stats.rxPacketsDropeedByHW << std::endl;

    file << std::setw(first_column_width) << "device.tx.packets.total: " << stats.aggregatedTxStats.packets << std::endl;
    file << std::setw(first_column_width) << "device.tx.packets.per/sec: " << stats.aggregatedTxStats.packetsPerSec << std::endl;
    file << std::setw(first_column_width) << "device.tx.bytes.total: " << stats.aggregatedTxStats.bytes << std::endl;
    file << std::setw(first_column_width) << "device.tx.bytes.per/sec: " << stats.aggregatedTxStats.bytesPerSec << std::endl;

    file << std::setw(first_column_width) << "ring.count: " << rte_ring_count(ctx.rx_ring.get()) << std::endl;
    file << std::setw(first_column_width) << "ring.free_count: " << rte_ring_free_count(ctx.rx_ring.get()) << std::endl;
    file << std::setw(first_column_width) << "ring.capacity: " << rte_ring_get_capacity(ctx.rx_ring.get()) << std::endl;

    file << std::setw(first_column_width) << "flow-pool.size: " << ctx.flow_pool.table.size() << std::endl;

    file.close();

}
