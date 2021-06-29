#ifndef SERVER42_ISLMETADATA_H
#define SERVER42_ISLMETADATA_H

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

#include "IslId.h"
#include "PacketPool.h"

namespace org::openkilda {

    namespace bmi = boost::multi_index;

    class IslMetadata {

        isl_endpoint_t isl_endpoint;
        std::string dst_mac;
        int32_t hash;

    public:
        IslMetadata(std::string switch_id, uint16_t port, std::string dst_mac, int32_t hash)
                : dst_mac(std::move(dst_mac)), hash(hash) {

            isl_endpoint = org::openkilda::make_isl_endpoint(switch_id, port);
        }

        const std::string &get_switch_id() const {

            return std::get<int(isl_endpoint_members::switch_id)>(isl_endpoint);
        };

        const isl_endpoint_t &get_isl_endpoint() const {
            return isl_endpoint;
        };

        uint16_t get_port() const {
            return std::get<int(isl_endpoint_members::port)>(isl_endpoint);
        };

        const std::string &get_dst_mac() const {
            return dst_mac;
        };

        int32_t get_hash() const {
            return hash;
        };

        bool operator<(const IslMetadata &i) const { return isl_endpoint < i.isl_endpoint; }
    };

    typedef bmi::multi_index_container<
            std::shared_ptr<IslMetadata>,
            bmi::indexed_by<
                    bmi::ordered_unique<bmi::const_mem_fun<IslMetadata, const isl_endpoint_t &, &IslMetadata::get_isl_endpoint> >,
                    bmi::ordered_non_unique<bmi::const_mem_fun<IslMetadata, const std::string &, &IslMetadata::get_switch_id>>
            >
    > isl_metadata_set_t;

    class IslMetadataContainer
            : public IPacketPoolMetadataDb<isl_endpoint_t, std::shared_ptr<IslMetadata>> {
    public:
        typedef std::shared_ptr<IslMetadata> value_t;

        isl_metadata_set_t metadata_set;

        value_t null_ptr;

        void set(const isl_endpoint_t &isl_endpoint, const value_t &metadata) {
            metadata_set.insert(metadata);
        }

        virtual const value_t &get(const isl_endpoint_t &isl_endpoint) const {
            const isl_metadata_set_t::nth_index<0>::type &isl_endpoint_index = metadata_set.get<0>();
            auto it = isl_endpoint_index.find(isl_endpoint);
            if (it != isl_endpoint_index.end()) {
                return *it;
            }
            return null_ptr;
        }

        void remove(const isl_endpoint_t &isl_endpoint) {
            isl_metadata_set_t::nth_index<0>::type &isl_endpoint_index = metadata_set.get<0>();
            auto it = isl_endpoint_index.find(isl_endpoint);
            if (it != isl_endpoint_index.end()) {
                isl_endpoint_index.erase(it);
            }
        }

        virtual std::list<isl_endpoint_t> get_endpoint_from_switch(const std::string &switch_id) const {
            const isl_metadata_set_t::nth_index<1>::type &dst_mac_index = metadata_set.get<1>();
            isl_metadata_set_t::nth_index<1>::type::iterator its, ite;
            std::tie(its, ite) = dst_mac_index.equal_range(switch_id);

            std::list<isl_endpoint_t> result;

            for (auto i = its; i != ite; ++i) {
                result.emplace_back(i->get()->get_isl_endpoint());
            }
            return result;
        }

        void clear() {
            metadata_set.clear();
        }
    };

}

#endif //SERVER42_ISLMETADATA_H
