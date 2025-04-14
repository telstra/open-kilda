#ifndef SERVER42_SHAREDCONTEXT_H
#define SERVER42_SHAREDCONTEXT_H

#include <mutex>

#include <rte_ring.h>

#include <pcapplusplus/DpdkDevice.h>

#include "PacketPool.h"
#include "PacketGenerator.h"


namespace org::openkilda {
    struct SharedContext {
        pcpp::DpdkDevice *primary_device;
        std::mutex &pool_guard;
        flow_pool_t &flow_pool;
        isl_pool_t &isl_pool;
        boost::shared_ptr<rte_ring> rx_ring;
        int mbuf_dyn_timestamp_offset;
    };
}

#endif //SERVER42_SHAREDCONTEXT_H
