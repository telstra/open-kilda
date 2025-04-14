#ifndef SERVER42_WORKERS_H
#define SERVER42_WORKERS_H

#include <mutex>

#include <boost/atomic.hpp>
#include <boost/shared_ptr.hpp>

#include <rte_ring.h>

#include <pcapplusplus/DpdkDevice.h>

#include "PacketGenerator.h"

namespace org::openkilda {

    bool write_thread(boost::atomic<bool> &alive,
                      pcpp::DpdkDevice *device,
                      flow_pool_t &flow_pool,
                      std::mutex &pool_guard,
                      isl_pool_t &isl_pool);

    bool read_thread(
            boost::atomic<bool> &alive,
            pcpp::DpdkDevice *device,
            boost::shared_ptr<rte_ring> ring,
            int mbuf_dyn_timestamp_offset);

    bool process_thread(uint32_t core_id,
                        boost::atomic<bool> &alive,
                        boost::shared_ptr<rte_ring> rx_ring,
                        uint16_t zmq_port,
                        const pcpp::MacAddress &src_mac,
                        int mbuf_dyn_timestamp_offset);

    bool echo_thread(boost::atomic<bool> &alive,
                     pcpp::DpdkDevice* device);
}

#endif //SERVER42_WORKERS_H
