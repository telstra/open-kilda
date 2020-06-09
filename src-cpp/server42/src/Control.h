#ifndef SERVER42_CONTROL_H
#define SERVER42_CONTROL_H

#include "SharedContext.h"

namespace org::openkilda {


    using buffer_t = std::vector<boost::uint8_t>;

    buffer_t handle_command_request(const void* data, int size, SharedContext& ctx);
}

#endif //SERVER42_CONTROL_H
