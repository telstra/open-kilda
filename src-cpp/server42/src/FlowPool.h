#ifndef FLOW_POOL_H
#define FLOW_POOL_H

#include <map>
#include <string>
#include <vector>
#include <list>
#include <boost/shared_ptr.hpp>

namespace org::openkilda {

    template<typename F = std::string, typename M = int64_t>
    class IFlowPoolMetadataDb {
    public:
        typedef M value_t;

        virtual const M &get(const F &flow_endpoint) const = 0;
        virtual std::list<F> get_flow_from_switch(const std::string &dst_mac) const = 0;
    };


    template<typename F = std::string, typename M = int64_t>
    class FlowPoolMetadataDbDummyImpl
            : public IFlowPoolMetadataDb<F, M> {
    public:
        typedef F flow_endpoint_t;
        typedef M value_t;

        void set(const flow_endpoint_t &flow_endpoint, const value_t &metadata) {

        }

        void remove(const flow_endpoint_t &flow_endpoint) {

        }

        const value_t &get(const flow_endpoint_t &flow_endpoint) const {
            throw std::logic_error("not implemented");
        }

        virtual std::list<F> get_flow_from_switch(const std::string &dst_mac) const {
            return std::list<F>();
        }

        void clear() {

        }
    };


    template<typename A, typename F = std::string, typename M = FlowPoolMetadataDbDummyImpl<F, int64_t> >
    class FlowPool {
        typedef typename A::value_t value_t;
        typedef std::map<F, size_t> locator_t;
        typedef std::vector<value_t> table_t;
        typedef std::vector<F> flowid_table_t;
    private:
        locator_t locator;
        M metadata_db;

        flowid_table_t flow_endpoint_table;

    public:

        typedef F flow_endpoint_t;

        typedef A allocator_t;
        typedef M metadata_db_t;
        table_t table;

        explicit FlowPool() = default;

        FlowPool(const FlowPool &) = delete;

        ~FlowPool() {
            for (auto p: table) {
                A::dealocate(p);
            }
        }

        bool add_flow(const F &flow_endpoint, const value_t &raw_packet, const typename M::value_t &metadata) {
            if (locator.find(flow_endpoint) != locator.end()) {
                return false;
            }
            locator[flow_endpoint] = table.size();
            table.push_back(raw_packet);
            flow_endpoint_table.push_back(flow_endpoint);
            metadata_db.set(flow_endpoint, metadata);
            return true;
        }

        void remove_flow(const F &flow_endpoint) {
            auto location_it = locator.find(flow_endpoint);
            if (location_it == locator.end()) {
                return;
            }

            size_t location_index = location_it->second;

            value_t packet = table[location_index];

            table[location_index] = table.back();
            table.pop_back();

            locator[flow_endpoint_table.back()] = location_index;
            locator.erase(location_it);

            flow_endpoint_table[location_index] = flow_endpoint_table.back();
            flow_endpoint_table.pop_back();

            metadata_db.remove(flow_endpoint);

            // free old mbuff
            A::dealocate(packet);
        }

        void clear() {
            for (auto p: table) {
                A::dealocate(p);
            }
            locator.clear();
            flow_endpoint_table.clear();
            table.clear();
            metadata_db.clear();
        }

        flowid_table_t const &get_flowid_table() {
            return flow_endpoint_table;
        }

        IFlowPoolMetadataDb<F, typename M::value_t> const* get_metadata_db() {
            return &metadata_db;
        }
    };
}

#endif // FLOW_POOL_H
