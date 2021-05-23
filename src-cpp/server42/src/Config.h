#ifndef CONFIG_H
#define CONFIG_H

#include <boost/shared_ptr.hpp>
#include <boost/cstdint.hpp>
#include <boost/ref.hpp>
#include "SharedContext.h"


namespace org::openkilda {

    class Config {

    protected:
        virtual ~Config() = default;

    public:

        virtual uint32_t get_core_mask() const = 0;

        virtual uint32_t get_master_lcore() const = 0;

        /*
         The size of the mbuf pool size dictates how many opackets can be handled by the application at the same time.
         For example: if pool size is 1023 it means that no more than 1023 packets can be handled or stored in
         application memory at every point in time
        */
        virtual uint32_t get_mbuf_pool_size_per_device() const = 0;

        virtual uint32_t get_process_queue_size() const = 0;

        virtual uint32_t get_first_stats_port() const = 0;

        virtual uint32_t get_control_port() const = 0;

        virtual bool is_debug() const = 0;
        
        using ptr = boost::shared_ptr<const Config>;

        using cref_ptr = const ptr&;

        // CONST SECTION

        static constexpr int primary_device_port_id = 0;
        static constexpr int loopback_device_port_id = 1;
        static constexpr int maximum_cores = 128;
        static constexpr int chunk_size = 64;
        static constexpr boost::uint16_t flow_rtt_generated_packet_udp_dst_port = 58168;
        static constexpr boost::uint16_t flow_rtt_caught_packet_udp_src_port = 4701;
        static constexpr boost::uint16_t isl_rtt_generated_packet_udp_dst_port = 58169;
        static constexpr boost::uint16_t isl_rtt_caught_packet_catch_udp_src_port = 4711;

        static constexpr int dpdk_device_configuration_receive_descriptors_number = 2048;
        static constexpr int dpdk_device_configuration_transmit_descriptors_number = 2048;
        static constexpr int dpdk_device_configuration_flush_tx_buffer_timeout = 100;
        static constexpr int dpdk_device_configuration_rss_hash_function = 0;

        static constexpr const char *ring_name = "rx-ring";
    };

    int create_config_from_cmd(int argc, char **argv, Config::ptr&);

    void save_stats_to_file(const Config::ptr &config, org::openkilda::SharedContext &ctx);
}

#endif // CONFIG_H