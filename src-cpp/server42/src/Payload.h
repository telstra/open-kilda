#pragma once

namespace org::openkilda {

    struct FlowPayload {
        int64_t t0;
        int64_t t1;
        bool direction;
        uint8_t flow_id_offset;
        size_t flow_id_length;
    };

    struct IslPayload {
        int64_t t0;
        int64_t t1;
        char switch_id[24];
        int32_t port;
    };
}
