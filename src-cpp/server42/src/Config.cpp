#include "Config.h"

#include <algorithm>
#include <bitset>

#include <boost/program_options.hpp>
#include <boost/log/trivial.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/smart_ptr/make_shared.hpp>

namespace org::openkilda {

    namespace po = boost::program_options;

    class InvalidCmd : public std::exception {
    };

    class Config_impl : public Config {
    private:
        Config_impl(Config_impl const &) = delete;

        Config_impl &operator=(Config_impl const &) = delete;

        uint32_t core_mask;
        uint32_t master_lcore;
        uint32_t mbuf_pool_size_per_device;
        uint32_t process_queue_size;
        uint32_t first_stats_port;
        uint32_t control_port;
        bool debug;

    public:

        Config_impl() = default;

        void fill_from_cmd(int argc, char **argv) {

            po::variables_map vm = parse_cmd(argc, argv);

            process_core_mask(vm);

            process_master_lcore(vm);

            process_mbuf_pool_size_per_device(vm);

            process_process_queue_size(vm);

            process_first_stats_port(vm);

            process_control_port(vm);

            process_debug(vm);
        }

        uint32_t get_core_mask() const override {
            return core_mask;
        }

        uint32_t get_master_lcore() const override {
            return master_lcore;
        }

        uint32_t get_mbuf_pool_size_per_device() const override {
            return mbuf_pool_size_per_device;
        }

        uint32_t get_process_queue_size() const override {
            return process_queue_size;
        }

        uint32_t get_first_stats_port() const override {
            return first_stats_port;
        }

        uint32_t get_control_port() const override {
            return control_port;
        }

        virtual bool is_debug() const override {
            return debug;
        }

        constexpr static const char *arg_core = "c";
        constexpr static const char *arg_master_core = "master-lcore";
        constexpr static const char *arg_mbuf_pool_size_per_device = "kilda-mbuf-pool-size-per-device";
        constexpr static const char *arg_process_queue_size = "process-queue-size";
        constexpr static const char *arg_first_stats_port = "first-stats-port";
        constexpr static const char *arg_control_port = "control-port";
        constexpr static const char *arg_debug = "debug";

    private:

        po::variables_map parse_cmd(uint32_t argc, char **argv) {
            // remove "--" from argv
            int argc_actual = argc;
            char **argv_actual = argv;

            std::vector<char *> argv_filtered;

            std::copy_if(argv_actual, argv_actual + argc_actual,
                         std::back_inserter(argv_filtered), [](char *s) { return std::string(s) != "--"; });

            if (argc != argv_filtered.size()) {
                argv_actual = argv_filtered.data();
                argc_actual = argv_filtered.size();
            }

            // end of filtering

            po::variables_map vm;
            po::store(
                    po::command_line_parser(argc_actual, argv_actual)
                            .options(make_description())
                            .style(
                                    po::command_line_style::unix_style
                                    | po::command_line_style::allow_long_disguise)
                            .allow_unregistered()
                            .run(),
                    vm);

            return vm;
        }

        po::options_description make_description() {
            po::options_description desc;
            desc.add_options()
                    (arg_core, po::value<std::string>(), "Set the hexadecimal bitmask of the cores to run on.")
                    (arg_master_core, po::value<int>()->default_value(0), "Core ID that is used as master.")
                    (arg_mbuf_pool_size_per_device, po::value<int>()->default_value(4095),
                     "The mbuf pool size each DpdkDevice will have. Must be 2^N-1")
                    (arg_process_queue_size, po::value<int>()->default_value(32768),
                     "Size of queue from read thread and send thread Must be power of 2")
                    (arg_first_stats_port, po::value<int>()->default_value(5556), "First stats port for zmq")
                    (arg_control_port, po::value<int>()->default_value(5555), "Control port for zmq")
                    (arg_debug, po::bool_switch());
            return desc;
        }

        void process_core_mask(const po::variables_map &vm) {

            if (vm.count("c")) {
                std::string core_mask_option = vm["c"].as<std::string>();
                BOOST_LOG_TRIVIAL(info) << "core mask: " << core_mask_option;
                core_mask = std::stoul(core_mask_option, 0, 16);

                if (core_mask == 0) {
                    BOOST_LOG_TRIVIAL(fatal) << "core mask must be greater than 0";
                    throw InvalidCmd{};
                }

                std::bitset<128> cores(core_mask);
                for (size_t i = 0; i < cores.size(); ++i) {
                    if (cores.test(i)) {
                        BOOST_LOG_TRIVIAL(info) << "core " << i << " enabled for dpkd";
                    }
                }

            } else {
                BOOST_LOG_TRIVIAL(fatal) << "core mask must be set";
                throw InvalidCmd{};
            }
        }

        void process_master_lcore(const po::variables_map &vm) {
            master_lcore = vm[arg_master_core].as<int>();
            BOOST_LOG_TRIVIAL(info) << "master-lcore " << master_lcore;
        }

        void process_mbuf_pool_size_per_device(const po::variables_map &vm) {
            mbuf_pool_size_per_device = vm[arg_mbuf_pool_size_per_device].as<int>();
            BOOST_LOG_TRIVIAL(info) << "mbuf-pool-size-per-device " << mbuf_pool_size_per_device;
        }

        void process_process_queue_size(const po::variables_map &vm) {
            process_queue_size = vm[arg_process_queue_size].as<int>();
            BOOST_LOG_TRIVIAL(info) << "process-queue-size " << process_queue_size;
        }

        void process_first_stats_port(const po::variables_map &vm) {
            first_stats_port = vm[arg_first_stats_port].as<int>();
            BOOST_LOG_TRIVIAL(info) << "first-stats-port " << first_stats_port;
        }

        void process_control_port(const po::variables_map &vm) {
            control_port = vm[arg_control_port].as<int>();
            BOOST_LOG_TRIVIAL(info) << "control-port " << control_port;
        }

        void process_debug(const po::variables_map &vm) {
            debug = vm[arg_debug].as<bool>();
            BOOST_LOG_TRIVIAL(info) << "debug " << debug;
        }

    };

    int create_config_from_cmd(int argc, char **argv, Config::ptr &config) {
        auto p = boost::make_shared<Config_impl>();
        try {
            p->fill_from_cmd(argc, argv);
        } catch (InvalidCmd _) {
            return 1;
        }
        config = std::move(p);
        return 0;
    }

}