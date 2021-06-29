#include "DumpStats.h"

#include <fstream>
#include <iomanip>


template <typename V>
void write_metric(std::ofstream& file, const std::string& name, const std::string& desc, const V value) {
    file << "# HELP " << name << " " << desc << std::endl;
    file << "# TYPE " << name << " gauge" << std::endl;
    file << name << " " << value << std::endl;
}

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

    unsigned int ring_count = rte_ring_count(ctx.rx_ring.get());
    unsigned int ring_free_count = rte_ring_free_count(ctx.rx_ring.get());
    unsigned int ring_capacity = rte_ring_get_capacity(ctx.rx_ring.get());

    file << std::setw(first_column_width) << "ring.count: " << ring_count << std::endl;
    file << std::setw(first_column_width) << "ring.free_count: " << ring_free_count << std::endl;
    file << std::setw(first_column_width) << "ring.capacity: " << ring_capacity << std::endl;

    unsigned long flow_pool_size = ctx.flow_pool.table.size();
    file << std::setw(first_column_width) << "flow-pool.size: " << flow_pool_size << std::endl;
    unsigned long isl_pool_size = ctx.isl_pool.table.size();
    file << std::setw(first_column_width) << "isl-pool.size: " << isl_pool_size << std::endl;

    file.close();

    std::ofstream file_prometheus;
    file_prometheus.open ("/tmp/server42-prometheus.txt", std::ios::out | std::ios::trunc);

    write_metric(file_prometheus, "device_rx_packets_total",  "Total number of rx packets", stats.aggregatedRxStats.packets);
    write_metric(file_prometheus, "device_rx_packets_per_sec", "Rx packets per second", stats.aggregatedRxStats.packetsPerSec);

    write_metric(file_prometheus, "device_rx_bytes_total", "Total number of successfully received rx bytes", stats.aggregatedRxStats.bytes);
    write_metric(file_prometheus, "device_rx_bytes_per_sec" , "Rx bytes per second", stats.aggregatedRxStats.bytesPerSec);
    write_metric(file_prometheus, "device_rx_erroneous_packets", "Total number of rx erroneous packets", stats.rxErroneousPackets);
    write_metric(file_prometheus, "device_rx_mbuf_aloc_failed", "Total number of rx mbuf allocation failuers",  stats.rxMbufAlocFailed);
    write_metric(file_prometheus, "device_rx_packets_dropped_by_hw", "Total number of RX packets dropped by H/W because there are no available buffers (i.e RX queues are full)", stats.rxPacketsDropeedByHW);

    write_metric(file_prometheus, "device_tx_packets_total", "Total number of tx packets", stats.aggregatedTxStats.packets);
    write_metric(file_prometheus, "device_tx_packets_per_sec", "Tx packets per second", stats.aggregatedTxStats.packetsPerSec);
    write_metric(file_prometheus, "device_tx_bytes_total", "Total number of successfully transmitted bytes", stats.aggregatedTxStats.bytes);
    write_metric(file_prometheus, "device_tx_bytes_per_sec", "Tx bytes per second", stats.aggregatedTxStats.bytesPerSec);

    write_metric(file_prometheus, "ring_count" , "Items in ring", ring_count);
    write_metric(file_prometheus, "ring_free_count" , "Free places in ring", ring_free_count);
    write_metric(file_prometheus, "ring_capacity" , "Ring capacity", ring_capacity);

    write_metric(file_prometheus, "flow_pool_size" , "Pool size of flow endpoints", flow_pool_size);
    write_metric(file_prometheus, "isl_pool_size" , "Pool size of isl endpoints", isl_pool_size);

    file_prometheus.close();
}
