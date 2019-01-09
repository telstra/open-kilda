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

package org.openkilda.wfm.topology.stats.metrics;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.JsonEncodeException;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SystemRuleMetricGenBolt extends MetricGenBolt {

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        FlowStatsData data = (FlowStatsData) message.getData();
        log.debug("Received system rules statistics: {}.", data);
        long timestamp = message.getTimestamp();
        SwitchId switchId = data.getSwitchId();

        try {
            for (FlowStatsEntry entry : data.getStats()) {
                emit(entry, timestamp, switchId);
            }
        } catch (Exception e) {
            log.error("Unhandled exception: {}", e.getMessage(), e);
        } finally {
            collector.ack(input);
        }
    }

    private void emit(FlowStatsEntry entry, long timestamp, SwitchId switchId) throws JsonEncodeException {
        if (!Cookie.isDefaultRule(entry.getCookie())) {
            log.warn("Received statistic is not for system rule. Cookie: '{}'", entry.getCookie());
        }

        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("cookieHex", Cookie.toString(entry.getCookie()));

        collector.emit(tuple("pen.switch.flow.system.packets", timestamp, entry.getPacketCount(), tags));
        collector.emit(tuple("pen.switch.flow.system.bytes", timestamp, entry.getByteCount(), tags));
        collector.emit(tuple("pen.switch.flow.system.bits", timestamp, entry.getByteCount() * 8, tags));
    }
}
