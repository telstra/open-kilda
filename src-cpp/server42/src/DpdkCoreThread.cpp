#include "DpdkCoreThread.h"

#include <boost/log/trivial.hpp>

namespace org::openkilda {

    DpdkCoreThread::DpdkCoreThread(thread_t thread_function) :
            thread(std::move(thread_function)) {
    }

    bool DpdkCoreThread::run(uint32_t used_core_id) {
        alive.store(true);
        core_id = used_core_id;

        bool ret = thread(core_id, alive);

        thread.clear();

        if (alive.load()) {
            BOOST_LOG_TRIVIAL(error) << "Thread shutdown";
        } else {
            BOOST_LOG_TRIVIAL(info) << "Thread shutdown";
        }

        return ret;
    }

    void DpdkCoreThread::stop() {
        alive.store(false);
    }

    uint32_t DpdkCoreThread::getCoreId() const {
        return core_id;
    }

}