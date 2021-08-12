/* Copyright 2018 Telstra Open Source
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

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class PortMetricGenBolt extends MetricGenBolt {

    public PortMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        InfoMessage message = (InfoMessage) input.getValueByField(KafkaRecordTranslator.FIELD_ID_PAYLOAD);
        PortStatsData data = (PortStatsData) message.getData();
        long timestamp = message.getTimestamp();

        for (PortStatsEntry entry : data.getStats()) {
            emit(entry, timestamp, data.getSwitchId());
        }
    }

    private void emit(PortStatsEntry entry, long timestamp, SwitchId switchId) {
        Map<String, String> tags = ImmutableMap.of(
                "switchid", switchId.toOtsdFormat(),
                "port", String.valueOf(entry.getPortNo())
        );

        emitMetric("switch.rx-packets", timestamp, entry.getRxPackets(), tags);
        emitMetric("switch.tx-packets", timestamp, entry.getTxPackets(), tags);
        emitMetric("switch.rx-bytes", timestamp, entry.getRxBytes(), tags);
        emitMetric("switch.rx-bits", timestamp, entry.getRxBytes() * 8, tags);
        emitMetric("switch.tx-bytes", timestamp, entry.getTxBytes(), tags);
        emitMetric("switch.tx-bits", timestamp, entry.getTxBytes() * 8, tags);
        emitMetric("switch.rx-dropped", timestamp, entry.getRxDropped(), tags);
        emitMetric("switch.tx-dropped", timestamp, entry.getTxDropped(), tags);
        emitMetric("switch.rx-errors", timestamp, entry.getRxErrors(), tags);
        emitMetric("switch.tx-errors", timestamp, entry.getTxErrors(), tags);
        emitMetric("switch.rx-frame-error", timestamp, entry.getRxFrameErr(), tags);
        emitMetric("switch.rx-over-error", timestamp, entry.getRxOverErr(), tags);
        emitMetric("switch.rx-crc-error", timestamp, entry.getRxCrcErr(), tags);
        emitMetric("switch.collisions", timestamp, entry.getCollisions(), tags);
    }
}
