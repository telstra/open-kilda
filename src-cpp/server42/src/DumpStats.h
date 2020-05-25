#ifndef SERVER42_DUMPSTATS_H
#define SERVER42_DUMPSTATS_H

#include <pcapplusplus/DpdkDevice.h>

#include "Config.h"
#include "SharedContext.h"

namespace org::openkilda {
    void save_stats_to_file(Config::cref_ptr config, SharedContext &ctx);
};

#endif //SERVER42_DUMPSTATS_H
