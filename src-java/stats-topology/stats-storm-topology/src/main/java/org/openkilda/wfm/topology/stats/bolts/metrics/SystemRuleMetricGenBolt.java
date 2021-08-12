/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.bolts.metrics;

import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;

import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SystemRuleMetricGenBolt extends MetricGenBolt {

    public SystemRuleMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        FlowStatsData data = (FlowStatsData) input.getValueByField(STATS_FIELD);
        log.debug("Received system rules statistics: {}.", data);
        long timestamp = getCommandContext().getCreateTime();
        SwitchId switchId = data.getSwitchId();

        for (FlowStatsEntry entry : data.getStats()) {
            emit(entry, timestamp, switchId);
        }
    }

    private void emit(FlowStatsEntry entry, long timestamp, SwitchId switchId) {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("cookieHex", Cookie.toString(entry.getCookie()));

        emitMetric("switch.flow.system.packets", timestamp, entry.getPacketCount(), tags);
        emitMetric("switch.flow.system.bytes", timestamp, entry.getByteCount(), tags);
        emitMetric("switch.flow.system.bits", timestamp, entry.getByteCount() * 8, tags);
    }
}
