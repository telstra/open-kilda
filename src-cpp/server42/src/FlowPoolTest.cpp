#define BOOST_TEST_MODULE server42 tests

#include <boost/test/unit_test.hpp>

#include "FlowPool.h"
#include "FlowMetadata.h"
#include "statistics.pb.h"

namespace bmi = boost::multi_index;
namespace ok = org::openkilda;

class BasicAllocator {
public:
    typedef int *value_t;

    static value_t allocate(int v) {
        return new int(v);
    }

    static void dealocate(value_t v) {
        delete (v);
    }
};


BOOST_AUTO_TEST_CASE(flow_pool_basic_add) {

    org::openkilda::FlowPool<BasicAllocator> flow;

    flow.add_flow("test-01", BasicAllocator::allocate(100), 1);
    flow.add_flow("test-02", BasicAllocator::allocate(1000), 2);
    flow.remove_flow("test-01");

    BOOST_TEST(flow.table.size() == 1);
}

#define C1M 150
//#define C1M 3

BOOST_AUTO_TEST_CASE(flow_pool_basic_add_1m) {
    org::openkilda::FlowPool<BasicAllocator> flow;
    for (uint32_t i = 0; i < C1M; ++i) {
        std::stringstream ss;
        ss << "test-" << i;
        flow.add_flow(ss.str(), BasicAllocator::allocate(i), i);
    }

    std::cout << flow.table.size();

    BOOST_TEST(flow.table.size() == C1M);

    for (uint32_t i = 0; i < C1M; ++i) {
        std::stringstream ss;
        ss << "test-" << i;
        flow.remove_flow(ss.str());
        //std::cout << flow.table.size() << "\n" << std::flush;
    }

    BOOST_TEST(flow.table.size() == 0);
}

BOOST_AUTO_TEST_CASE(flow_pool_basic_iteration_1m) {

    org::openkilda::FlowPool<BasicAllocator> flow;
    for (uint32_t i = 0; i < C1M; ++i) {
        std::stringstream ss;
        ss << "test-" << i;
        flow.add_flow(ss.str(), BasicAllocator::allocate(i), i);
    }

    int **start = flow.table.data();
    int **end = flow.table.data() + flow.table.size();

    for (int **pos = start; pos < end; pos += 64) {
        //m_Device->sendPackets(pos, std::min(64L, end - pos));
        std::cout << **pos << "-" << std::min(64L, end - pos) << "\n" << std::flush;

    }
}


BOOST_AUTO_TEST_CASE(pb_test) {

    const int buff_size = 256;
    uint8_t buff[buff_size];

    auto start_time = std::chrono::time_point_cast<std::chrono::milliseconds>(
            std::chrono::high_resolution_clock::now());

    for (uint32_t i = 0; i < 2000000; i++) {
        org::openkilda::server42::stats::messaging::FlowLatencyPacket flowLatencyPacket;
        flowLatencyPacket.set_flow_id("payload->flow_id");
        flowLatencyPacket.set_t0(i * 2);
        flowLatencyPacket.set_t1(i * 3);
        flowLatencyPacket.set_packet_id(i * 4);
        //std::cout << flowLatencyPacket.ByteSizeLong();
        flowLatencyPacket.SerializePartialToArray(buff, buff_size);
    }

    auto end_time = std::chrono::time_point_cast<std::chrono::milliseconds>(
            std::chrono::high_resolution_clock::now());
    auto duration = end_time - start_time;
    std::cout << duration.count() << "\n" << std::flush;
}


BOOST_AUTO_TEST_CASE(flow_metadata_test_leaks) {

    org::openkilda::FlowMetadataContainer db;

    std::shared_ptr<org::openkilda::FlowMetadata> m = std::make_shared<org::openkilda::FlowMetadata>("flowId", false, "00:01", 0);
    std::weak_ptr<ok::FlowMetadata> weak = m;

    auto flow_endpoint = m->get_flow_endpoint();

    db.set(flow_endpoint, m);

    m.reset();

    BOOST_TEST(weak.use_count() == 1);

    db.remove(flow_endpoint);

    BOOST_TEST(weak.use_count() == 0);

}


namespace test {

    typedef bmi::multi_index_container<
            std::shared_ptr<ok::FlowMetadata>,
            bmi::indexed_by<
                    bmi::ordered_unique<bmi::const_mem_fun<ok::FlowMetadata, const ok::flow_endpoint_t &, &ok::FlowMetadata::get_flow_endpoint>>,
                    bmi::ordered_non_unique<bmi::const_mem_fun<ok::FlowMetadata, const std::string &, &ok::FlowMetadata::get_dst_mac>>
            >
    >
            metadata_set_t;

}

BOOST_AUTO_TEST_CASE(flow_metadata_play) {

    test::metadata_set_t set;

    auto f1 = std::shared_ptr<ok::FlowMetadata>(new ok::FlowMetadata("f1", false, "00:01", 0));
    auto f2 = std::shared_ptr<ok::FlowMetadata>(new ok::FlowMetadata("f2", false, "00:01", 0));
    auto f3 = std::shared_ptr<ok::FlowMetadata>(new ok::FlowMetadata("f3", false, "00:02", 0));

    set.insert(f1);
    set.insert(f2);
    set.insert(f3);

    test::metadata_set_t::nth_index<0>::type &flow_id_index = set.get<0>();

    auto it = flow_id_index.find(f1->get_flow_endpoint());
    bool result = it != flow_id_index.end();
    BOOST_TEST(result);

    it = flow_id_index.find(ok::make_flow_endpoint("flow_404", 0));
    result = it == flow_id_index.end();
    BOOST_TEST(result);

    test::metadata_set_t::nth_index<1>::type &dst_mac_index = set.get<1>();

    test::metadata_set_t::nth_index<1>::type::iterator it2s, it2e;

    std::tie(it2s, it2e) = dst_mac_index.equal_range(f1->get_dst_mac());

    BOOST_TEST(std::distance(it2s, it2e) == 2);

    it2s = dst_mac_index.find("40:40");

    result = it2s == dst_mac_index.end();

    BOOST_TEST(result);

    flow_id_index.erase(flow_id_index.find(f3->get_flow_endpoint()));

    BOOST_TEST(set.size() == 2);

    std::tie(it2s, it2e) = dst_mac_index.equal_range(f1->get_dst_mac());

    dst_mac_index.erase(it2s, it2e);

    BOOST_TEST(set.size() == 0);

}