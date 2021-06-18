#ifndef SERVER42_FLOWMETADATA_H
#define SERVER42_FLOWMETADATA_H

#include <map>
#include <string>
#include <utility>
#include <vector>
#include <list>
#include <boost/shared_ptr.hpp>

#include <boost/multi_index_container.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>

#include "FlowId.h"
#include "PacketPool.h"

namespace org::openkilda {

    namespace bmi = boost::multi_index;

    class FlowMetadata {

        flow_endpoint_t flow_endpoint;
        std::string dst_mac;
        int32_t hash;

    public:
        FlowMetadata(std::string flow_id, bool direction, std::string dst_mac, int32_t hash)
                : dst_mac(std::move(dst_mac)), hash(hash) {

            flow_endpoint = org::openkilda::make_flow_endpoint(flow_id, direction);
        }

        const std::string &get_flow_id() const {

            return std::get<int(flow_endpoint_members::flow_id)>(flow_endpoint);
        };

        const flow_endpoint_t &get_flow_endpoint() const {
            return flow_endpoint;
        };

        bool get_direction() const {
            return std::get<int(flow_endpoint_members::direction)>(flow_endpoint);
        };

        const std::string &get_dst_mac() const {
            return dst_mac;
        };

        int32_t get_hash() const {
            return hash;
        };

        bool operator<(const FlowMetadata &f) const { return flow_endpoint < f.flow_endpoint; }
    };

    typedef bmi::multi_index_container<
            std::shared_ptr<FlowMetadata>,
            bmi::indexed_by<
                    bmi::ordered_unique<bmi::const_mem_fun<FlowMetadata, const flow_endpoint_t &, &FlowMetadata::get_flow_endpoint> >,
                    bmi::ordered_non_unique<bmi::const_mem_fun<FlowMetadata, const std::string &, &FlowMetadata::get_dst_mac>>
            >
    > flow_metadata_set_t;

    class FlowMetadataContainer
            : public IPacketPoolMetadataDb<flow_endpoint_t, std::shared_ptr<FlowMetadata>> {
    public:
        typedef std::shared_ptr<FlowMetadata> value_t;

        flow_metadata_set_t metadata_set;

        value_t null_ptr;

        void set(const flow_endpoint_t &flow_endpoint, const value_t &metadata) {
            metadata_set.insert(metadata);
        }

        virtual const value_t &get(const flow_endpoint_t &flow_endpoint) const {
            const flow_metadata_set_t::nth_index<0>::type &flow_endpoint_index = metadata_set.get<0>();
            auto it = flow_endpoint_index.find(flow_endpoint);
            if (it != flow_endpoint_index.end()) {
                return *it;
            }
            return null_ptr;
        }

        void remove(const flow_endpoint_t &flow_endpoint) {
            flow_metadata_set_t::nth_index<0>::type &flow_endpoint_index = metadata_set.get<0>();
            auto it = flow_endpoint_index.find(flow_endpoint);
            if (it != flow_endpoint_index.end()) {
                flow_endpoint_index.erase(it);
            }
        }

        virtual std::list<flow_endpoint_t> get_endpoint_from_switch(const std::string &dst_mac) const {
            const flow_metadata_set_t::nth_index<1>::type &dst_mac_index = metadata_set.get<1>();
            flow_metadata_set_t::nth_index<1>::type::iterator its, ite;
            std::tie(its, ite) = dst_mac_index.equal_range(dst_mac);

            std::list<flow_endpoint_t> result;

            for (auto i = its; i != ite; ++i) {
                result.emplace_back(i->get()->get_flow_endpoint());
            }
            return result;
        }

        void clear() {
            metadata_set.clear();
        }
    };

}

#endif //SERVER42_FLOWMETADATA_H
