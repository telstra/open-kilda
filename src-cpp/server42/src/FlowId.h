#ifndef SERVER42_FLOWID_H
#define SERVER42_FLOWID_H


namespace org::openkilda {

    enum class flow_endpoint_members {
        flow_id, direction
    };

    using flow_endpoint_t = std::tuple<std::string, bool>;

    inline flow_endpoint_t make_flow_endpoint(const std::string &flow_id, bool direction) {
        return std::make_tuple(flow_id, direction);
    }

}

#endif //SERVER42_FLOWID_H
