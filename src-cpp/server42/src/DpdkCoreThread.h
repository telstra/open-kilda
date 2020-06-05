#ifndef SERVER42_DPDKCORETHREAD_H
#define SERVER42_DPDKCORETHREAD_H

#include <boost/function.hpp>
#include <boost/atomic.hpp>
#include <utility>


#include <pcapplusplus/DpdkDevice.h>
#include <pcapplusplus/DpdkDeviceList.h>

namespace org::openkilda {

    class DpdkCoreThread : public pcpp::DpdkWorkerThread {
        using thread_t = boost::function<bool(uint32_t core_id, boost::atomic<bool> &alive)>;
        u_int32_t core_id{};
        thread_t thread;
        boost::atomic<bool> alive{};
    public:

        DpdkCoreThread(thread_t thread_function);

        ~DpdkCoreThread() = default;

        bool run(uint32_t used_core_id) override;

        void stop() override;

        virtual uint32_t getCoreId() const override;
    };

}


#endif //SERVER42_DPDKCORETHREAD_H
