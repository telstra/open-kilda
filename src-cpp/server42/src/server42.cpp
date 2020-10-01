#include <unistd.h>
#include <iostream>

#include <mutex>
#include <bitset>
#include <thread>
#include <vector>
#include <chrono>

#include <zeromq/zmq.hpp>

#include <pcapplusplus/SystemUtils.h>
#include <pcapplusplus/DpdkDeviceList.h>

#include <boost/log/trivial.hpp>
#include <boost/stacktrace.hpp>
#include <boost/filesystem.hpp>
#include <boost/program_options.hpp>
#include <boost/coroutine2/all.hpp>
#include <boost/tuple/tuple.hpp>
#include <boost/format.hpp>
#include <boost/atomic/atomic.hpp>
#include <boost/bind.hpp>
#include <boost/make_shared.hpp>

#include <rte_memory.h>
#include <rte_ring.h>

#include "Config.h"
#include "control.pb.h"
#include "SharedContext.h"
#include "Control.h"
#include "Workers.h"
#include "DpdkCoreThread.h"


using namespace std::literals::chrono_literals;
using namespace org::openkilda;
using vector_worker_ptr_t = std::vector<boost::shared_ptr<DpdkCoreThread>>;
using worker_artefact_t = boost::tuple<boost::shared_ptr<DpdkCoreThread>, std::string>;


void segv_handler(int signum) {
    ::signal(signum, SIG_DFL);
    boost::stacktrace::safe_dump_to("./backtrace.dump");

    // NOT SAFE
    // ref https://www.boost.org/doc/libs/1_73_0/doc/html/stacktrace/getting_started.html#stacktrace.getting_started.handle_terminates_aborts_and_seg

    std::cerr << boost::stacktrace::stacktrace();

    ::raise(SIGABRT);
}

void init_trace() {

    ::signal(SIGSEGV, &segv_handler);
    ::signal(SIGABRT, &segv_handler);

    if (boost::filesystem::exists("./backtrace.dump")) {
        // there is a backtrace
        std::ifstream ifs("./backtrace.dump");

        boost::stacktrace::stacktrace st = boost::stacktrace::stacktrace::from_dump(ifs);
        std::cout << "Previous run crashed:\n" << st << std::endl;

        // cleaning up
        ifs.close();
        boost::filesystem::remove("./backtrace.dump");
    }
}

void init_logging(Config::cref_ptr config) {
    namespace logging = boost::log;
    if (config->is_debug()) {
        logging::core::get()->reset_filter();
    }
}

int init_dpdk(int argc, char *argv[], Config::cref_ptr config) {

    // Initialize DPDK
    int ret = rte_eal_init(argc, argv);
    if (ret < 0) {
        BOOST_LOG_TRIVIAL(fatal) << "failed to init the DPDK EAL";
        return 1;
    }

    ret = rte_eal_hpet_init(true);
    if (ret < 0) {
        BOOST_LOG_TRIVIAL(fatal) << "failed to init HPET";
        return 1;
    }

    pcpp::DpdkDeviceList::externalInitializationDpdk(config->get_core_mask(),
                                                     config->get_mbuf_pool_size_per_device(),
                                                     config->get_master_lcore());

    return 0;
}


pcpp::DpdkDevice *init_device(int port_id) {

    // Find DPDK devices
    pcpp::DpdkDevice *device = pcpp::DpdkDeviceList::getInstance().getDeviceByPort(port_id);
    if (device == nullptr) {
        BOOST_LOG_TRIVIAL(warning) << "Cannot find device with port " << port_id;
        return nullptr;
    }

    pcpp::DpdkDevice::DpdkDeviceConfiguration dpdk_config = pcpp::DpdkDevice::DpdkDeviceConfiguration(
            Config::dpdk_device_configuration_receive_descriptors_number,
            Config::dpdk_device_configuration_transmit_descriptors_number,
            Config::dpdk_device_configuration_flush_tx_buffer_timeout,
            Config::dpdk_device_configuration_rss_hash_function);

    // Open DPDK devices
    if (!device->openMultiQueues(1, 1, dpdk_config)) {
        BOOST_LOG_TRIVIAL(fatal)
            << "Couldn't open device port_id:" << port_id
            << "device_id: " << device->getDeviceId()
            << "PMD: " << device->getPMDName();
        return nullptr;
    }

    return device;
}

boost::shared_ptr<rte_ring> init_ring(Config::cref_ptr config) {

    return boost::shared_ptr<rte_ring>(rte_ring_create(
            Config::ring_name,
            config->get_process_queue_size(),
            SOCKET_ID_ANY,
            RING_F_SP_ENQ), rte_ring_free);
}


worker_artefact_t build_write_thread(SharedContext &shared_context) {
    auto ptr = boost::make_shared<DpdkCoreThread>(boost::bind(write_thread, _2,
                                                              shared_context.primary_device,
                                                              boost::ref(shared_context.flow_pool),
                                                              boost::ref(shared_context.flow_pool_guard)));
    return boost::make_tuple(ptr, "write_thread");
}


worker_artefact_t build_read_thread(SharedContext &shared_context) {
    auto ptr = boost::make_shared<DpdkCoreThread>(boost::bind(read_thread, _2,
                                                              shared_context.primary_device,
                                                              shared_context.rx_ring));
    return boost::make_tuple(ptr, "read_thread");
}

worker_artefact_t build_process_thread(SharedContext &shared_context,
                                       int process_thread_port,
                                       const pcpp::MacAddress &src_mac) {
    auto ptr = boost::make_shared<DpdkCoreThread>(
            boost::bind(process_thread, _1, _2,
                        shared_context.rx_ring,
                        process_thread_port,
                        src_mac));
    return boost::make_tuple(ptr, str(boost::format("process_thread with port %1%") % process_thread_port));
}

worker_artefact_t build_echo_thread(pcpp::DpdkDevice *loopback_device) {
    auto ptr = boost::make_shared<DpdkCoreThread>(boost::bind(echo_thread, _2, loopback_device));
    return boost::make_tuple(ptr,"echo_thread");
}

boost::tuple<int, vector_worker_ptr_t> init_workers(Config::cref_ptr config, SharedContext &shared_context) {
    using worker_builder_t = boost::coroutines2::coroutine<boost::tuple<boost::shared_ptr<DpdkCoreThread>, std::string>>;

    worker_builder_t::pull_type worker_source( // constructor enters coroutine-function
            [&](worker_builder_t::push_type &sink) {

                sink(build_write_thread(shared_context));

                sink(build_read_thread(shared_context));

                int process_thread_port = config->get_first_stats_port();
                const pcpp::MacAddress src_mac = shared_context.primary_device->getMacAddress();

                sink(build_process_thread(shared_context, process_thread_port, src_mac));

                if (pcpp::DpdkDevice *loopback_device = init_device(Config::loopback_device_port_id)) {
                    sink(build_echo_thread(loopback_device));
                }

                const int first_stats_port = config->get_first_stats_port();

                while (process_thread_port < first_stats_port + Config::maximum_cores) {
                    process_thread_port++;
                    sink(build_process_thread(shared_context, process_thread_port, src_mac));
                }
            });

    vector_worker_ptr_t workers;
    BOOST_LOG_TRIVIAL(info) << "Cores: ";
    std::bitset<Config::maximum_cores> cores(config->get_core_mask());
    for (size_t i = 0; i < cores.size() && worker_source; ++i) {
        if (i == config->get_master_lcore()) {
            BOOST_LOG_TRIVIAL(info) << "core " << i << " is master";
            cores.reset(i);
        } else if (cores.test(i)) {
            boost::shared_ptr<DpdkCoreThread> thread;
            std::string desc;
            boost::tie(thread, desc) = worker_source.get();
            BOOST_LOG_TRIVIAL(info) << "core " << i << " " << desc;
            workers.push_back(thread);
            worker_source();
        }
    }

    return boost::make_tuple(cores.to_ulong(), workers);
}

void init_interrupt_handler(boost::atomic<bool> &keep_running) {
    auto shutdown = [](void *cookie) {
        reinterpret_cast<boost::atomic<bool> *>(cookie)->store(false);
        BOOST_LOG_TRIVIAL(info) << "Shutdown signal recived";
    };
    pcpp::ApplicationEventHandler::getInstance().onApplicationInterrupted(shutdown, &keep_running);
}

int main(int argc, char *argv[]) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;

    init_trace();

    Config::ptr config;

    if (create_config_from_cmd(argc, argv, config)) {
        return 1;
    }

    init_logging(config);

    if (init_dpdk(argc, argv, config)) {
        return 1;
    }

    pcpp::DpdkDevice *device = init_device(Config::primary_device_port_id);

    if (!device) {
        BOOST_LOG_TRIVIAL(fatal) << "Cannot find primary device";
        return 1;
    }

    std::mutex flow_pool_guard;
    org::openkilda::flow_pool_t flow_pool;

    boost::shared_ptr<rte_ring> rx_ring = init_ring(config);
    if (!rx_ring) {
        BOOST_LOG_TRIVIAL(fatal) << "Cannot create ring";
        return 1;
    }

    SharedContext shared_context = {
            .primary_device = device,
            .flow_pool_guard = flow_pool_guard,
            .flow_pool = flow_pool,
            .rx_ring = rx_ring
    };

    boost::uint32_t cores;
    vector_worker_ptr_t workers;
    boost::tie(cores, workers) = init_workers(config, shared_context);

    std::vector<pcpp::DpdkWorkerThread*> pcpp_workers;
    for (auto w: workers) {
        pcpp_workers.emplace_back(w.get());
    }

    // Start capture in async mode
    if (!pcpp::DpdkDeviceList::getInstance().startDpdkWorkerThreads(cores, pcpp_workers)) {
        BOOST_LOG_TRIVIAL(fatal) << "Couldn't start worker threads";
        return 1;
    }

    BOOST_LOG_TRIVIAL(info) << "Init complete, process main loop";

    zmq::context_t context(1);
    context.setctxopt(ZMQ_THREAD_AFFINITY_CPU_ADD, config->get_master_lcore());
    zmq::socket_t socket(context, ZMQ_REP);
    std::string control_endpoint(str(boost::format("tcp://*:%1%") % config->get_control_port()));
    socket.bind(control_endpoint);
    BOOST_LOG_TRIVIAL(info) << "master zmq socket bind to " << control_endpoint;
    boost::atomic<bool> keep_running(true);
    init_interrupt_handler(keep_running);

    const uint64_t cycles_in_one_second = rte_get_timer_hz();
    uint64_t last_stats_dump_tsc = 0;
    while (keep_running.load()) {
        std::this_thread::sleep_for(1ms);
        try {
            zmq::message_t request;
            if (socket.recv(request, zmq::recv_flags::dontwait)) {
                buffer_t data = handle_command_request(request.data(), request.size(), shared_context);
                zmq::message_t message(data.data(), data.size());
                socket.send(message, zmq::send_flags::none);
            }
        } catch (zmq::error_t &exception) {
            BOOST_LOG_TRIVIAL(info) << "ZMQ Exception on main loop" << exception.what();
        } catch (std::exception &exception) {
            BOOST_LOG_TRIVIAL(info) << "Exception on main loop " << exception.what() << "\n";
        }

        // Write stats to file once per second
        if (rte_get_timer_cycles() - last_stats_dump_tsc >= cycles_in_one_second) {
            save_stats_to_file(config, shared_context);
            last_stats_dump_tsc = rte_get_timer_cycles();
        }
    }

    BOOST_LOG_TRIVIAL(info) << "End of main loop";

    // Stop worker threads
    pcpp::DpdkDeviceList::getInstance().stopDpdkWorkerThreads();

    BOOST_LOG_TRIVIAL(info) << "This is Ripley, last survivor of the Nostromo, signing off.";

    return 0;
}
