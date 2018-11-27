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

package org.openkilda.wfm.topology.stats.metrics;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.AbstractException;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class PortMetricGenBolt extends MetricGenBolt {

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);
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

        emitMetric("pen.switch.rx-packets", timestamp, entry.getRxPackets(), tags);
        emitMetric("pen.switch.tx-packets", timestamp, entry.getTxPackets(), tags);
        emitMetric("pen.switch.rx-bytes", timestamp, entry.getRxBytes(), tags);
        emitMetric("pen.switch.rx-bits", timestamp, entry.getRxBytes() * 8, tags);
        emitMetric("pen.switch.tx-bytes", timestamp, entry.getTxBytes(), tags);
        emitMetric("pen.switch.tx-bits", timestamp, entry.getTxBytes() * 8, tags);
        emitMetric("pen.switch.rx-dropped", timestamp, entry.getRxDropped(), tags);
        emitMetric("pen.switch.tx-dropped", timestamp, entry.getTxDropped(), tags);
        emitMetric("pen.switch.rx-errors", timestamp, entry.getRxErrors(), tags);
        emitMetric("pen.switch.tx-errors", timestamp, entry.getTxErrors(), tags);
        emitMetric("pen.switch.rx-frame-error", timestamp, entry.getRxFrameErr(), tags);
        emitMetric("pen.switch.rx-over-error", timestamp, entry.getRxOverErr(), tags);
        emitMetric("pen.switch.rx-crc-error", timestamp, entry.getRxCrcErr(), tags);
        emitMetric("pen.switch.collisions", timestamp, entry.getCollisions(), tags);
    }
}
