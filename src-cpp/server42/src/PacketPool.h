#ifndef PACKET_POOL_H
#define PACKET_POOL_H

#include <map>
#include <string>
#include <vector>
#include <list>
#include <boost/shared_ptr.hpp>

namespace org::openkilda {

    template<typename F = std::string, typename M = int64_t>
    class IPacketPoolMetadataDb {
    public:
        typedef M value_t;

        virtual const M &get(const F &packet_endpoint) const = 0;
        virtual std::list<F> get_endpoint_from_switch(const std::string &switch_key) const = 0;
    };


    template<typename F = std::string, typename M = int64_t>
    class PacketPoolMetadataDbDummyImpl
            : public IPacketPoolMetadataDb<F, M> {
    public:
        typedef F packet_endpoint_t;
        typedef M value_t;

        void set(const packet_endpoint_t &packet_endpoint, const value_t &metadata) {

        }

        void remove(const packet_endpoint_t &packet_endpoint) {

        }

        const value_t &get(const packet_endpoint_t &packet_endpoint) const {
            throw std::logic_error("not implemented");
        }

        virtual std::list<F> get_endpoint_from_switch(const std::string &switch_key) const {
            return std::list<F>();
        }

        void clear() {

        }
    };


    template<typename A, typename F = std::string, typename M = PacketPoolMetadataDbDummyImpl<F, int64_t> >
    class PacketPool {
        typedef typename A::value_t value_t;
        typedef std::map<F, size_t> locator_t;
        typedef std::vector<value_t> table_t;
        typedef std::vector<F> packetbyendpoint_table_t;
    private:
        locator_t locator;
        M metadata_db;

        packetbyendpoint_table_t packet_endpoint_table;

    public:

        typedef F packet_endpoint_t;

        typedef A allocator_t;
        typedef M metadata_db_t;
        table_t table;

        explicit PacketPool() = default;

        PacketPool(const PacketPool &) = delete;

        ~PacketPool() {
            for (auto p: table) {
                A::dealocate(p);
            }
        }

        bool add_packet(const F &packet_endpoint, const value_t &raw_packet, const typename M::value_t &metadata) {
            if (locator.find(packet_endpoint) != locator.end()) {
                return false;
            }
            locator[packet_endpoint] = table.size();
            table.push_back(raw_packet);
            packet_endpoint_table.push_back(packet_endpoint);
            metadata_db.set(packet_endpoint, metadata);
            return true;
        }

        void remove_packet(const F &packet_endpoint) {
            auto location_it = locator.find(packet_endpoint);
            if (location_it == locator.end()) {
                return;
            }

            size_t location_index = location_it->second;

            value_t packet = table[location_index];

            table[location_index] = table.back();
            table.pop_back();

            locator[packet_endpoint_table.back()] = location_index;
            locator.erase(location_it);

            packet_endpoint_table[location_index] = packet_endpoint_table.back();
            packet_endpoint_table.pop_back();

            metadata_db.remove(packet_endpoint);

            // free old mbuff
            A::dealocate(packet);
        }

        void clear() {
            for (auto p: table) {
                A::dealocate(p);
            }
            locator.clear();
            packet_endpoint_table.clear();
            table.clear();
            metadata_db.clear();
        }

        packetbyendpoint_table_t const &get_packetbyendpoint_table() {
            return packet_endpoint_table;
        }

        IPacketPoolMetadataDb<F, typename M::value_t> const* get_metadata_db() {
            return &metadata_db;
        }
    };
}

#endif // PACKET_POOL_H
