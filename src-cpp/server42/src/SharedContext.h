#ifndef SERVER42_SHAREDCONTEXT_H
#define SERVER42_SHAREDCONTEXT_H

#include <mutex>

#include <rte_ring.h>

#include <pcapplusplus/DpdkDevice.h>

#include "FlowPool.h"
#include "PacketGenerator.h"


namespace org::openkilda {
    struct SharedContext {
        pcpp::DpdkDevice *primary_device;
        std::mutex &flow_pool_guard;
        flow_pool_t &flow_pool;
        boost::shared_ptr<rte_ring> rx_ring;
    };
}

#endif //SERVER42_SHAREDCONTEXT_H
