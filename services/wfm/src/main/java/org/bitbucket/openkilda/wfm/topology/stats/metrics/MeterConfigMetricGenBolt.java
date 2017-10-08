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

package org.bitbucket.openkilda.wfm.topology.stats.metrics;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.bitbucket.openkilda.wfm.topology.stats.StatsComponentType;
import org.bitbucket.openkilda.wfm.topology.stats.StatsStreamType;

import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MeterConfigMetricGenBolt extends MetricGenBolt {
    private static final Logger logger = LoggerFactory.getLogger(MeterConfigMetricGenBolt.class);

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        logger.debug("Meter config stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        MeterConfigStatsData data = (MeterConfigStatsData) message.getData();
        long timestamp = message.getTimestamp();
        data.getStats().forEach(stats -> stats.getMeterIds().forEach(meterId -> {
            Map<String, String> tags = new HashMap<>();
            tags.put("switchid", data.getSwitchId().replaceAll(":", ""));
            tags.put("meterid", meterId.toString());
            collector.emit(tuple("pen.switch.meters", timestamp, meterId, tags));
        }));
        collector.ack(input);
    }
}
