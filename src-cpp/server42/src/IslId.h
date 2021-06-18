#ifndef SERVER42_ISLID_H
#define SERVER42_ISLID_H


namespace org::openkilda {

    enum class isl_endpoint_members {
        switch_id, port
    };

    using isl_endpoint_t = std::tuple<std::string, boost::int16_t>;

    inline isl_endpoint_t make_isl_endpoint(const std::string &switch_id, boost::uint16_t port) {
        return std::make_tuple(switch_id, port);
    }

}

#endif //SERVER42_ISLID_H
