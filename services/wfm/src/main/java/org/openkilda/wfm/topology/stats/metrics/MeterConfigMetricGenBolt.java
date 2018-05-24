/* Copyright 2017 Telstra Open Source
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

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.StatsStreamType;
import org.openkilda.wfm.topology.utils.StatsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class MeterConfigMetricGenBolt extends MetricGenBolt {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeterConfigMetricGenBolt.class);

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        LOGGER.debug("Meter config stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        MeterConfigStatsData data = (MeterConfigStatsData) message.getData();
        long timestamp = message.getTimestamp();

        try {
            String switchId = StatsUtil.formatSwitchId(data.getSwitchId());
            for (MeterConfigReply reply : data.getStats()) {
                for (Long meterId : reply.getMeterIds()) {
                    emit(timestamp, meterId, switchId);
                }
            }
        } finally {
            collector.ack(input);
        }
    }

    private void emit(long timestamp, Long meterId, String switchId) {
        try {
            Map<String, String> tags = ImmutableMap.of(
                    "switchid", switchId,
                    "meterId", meterId.toString()
            );
            collector.emit(tuple("pen.switch.meters", timestamp, meterId, tags));
        } catch (JsonEncodeException e) {
            LOGGER.error("Error during serialization of datapoint", e);
        }
    }
}
