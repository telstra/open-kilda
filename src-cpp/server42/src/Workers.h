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
                      flow_pool_t &pool,
                      std::mutex &pool_guard);

    bool read_thread(
            boost::atomic<bool> &alive,
            pcpp::DpdkDevice *device,
            boost::shared_ptr<rte_ring> ring);

    bool process_thread(uint32_t core_id,
                        boost::atomic<bool> &alive,
                        boost::shared_ptr<rte_ring> rx_ring,
                        uint16_t zmq_port,
                        const pcpp::MacAddress &src_mac);

    bool echo_thread(boost::atomic<bool> &alive,
                     pcpp::DpdkDevice* device);
}

#endif //SERVER42_WORKERS_H
