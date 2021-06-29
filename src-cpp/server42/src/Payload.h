#pragma once

namespace org::openkilda {

    struct FlowPayload {
        int64_t t0;
        int64_t t1;
        char flow_id[32];
        bool direction;
    };

    struct IslPayload {
        int64_t t0;
        int64_t t1;
        char switch_id[24];
        int32_t port;
    };
}
