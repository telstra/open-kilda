/* Copyright 2021 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.flowmonitoring.bolt;

import org.openkilda.wfm.share.bolt.MonotonicClock;
import org.openkilda.wfm.topology.flowmonitoring.bolt.TickBolt.TickId;

public class TickBolt extends MonotonicClock<TickId> {
    public TickBolt(int cacheCheckInterval, int slaCheckInterval) {
        super(createConfig(cacheCheckInterval, slaCheckInterval));
    }

    private static ClockConfig<TickId> createConfig(int cacheCheckInterval, int slaCheckInterval) {
        ClockConfig<TickId> config = new ClockConfig<>();
        config.addTickInterval(TickId.CACHE_UPDATE, cacheCheckInterval);
        config.addTickInterval(TickId.SLA_CHECK, slaCheckInterval);
        return config;
    }

    enum TickId {
        CACHE_UPDATE,
        SLA_CHECK
    }
}
