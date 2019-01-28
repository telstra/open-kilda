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

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.AbstractException;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class MeterConfigMetricGenBolt extends MetricGenBolt {

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);
        MeterConfigStatsData data = (MeterConfigStatsData) message.getData();
        long timestamp = message.getTimestamp();

        SwitchId switchId = data.getSwitchId();
        for (MeterConfigReply reply : data.getStats()) {
            for (Long meterId : reply.getMeterIds()) {
                emit(timestamp, meterId, switchId);
            }
        }
    }

    private void emit(long timestamp, Long meterId, SwitchId switchId) {
        Map<String, String> tags = ImmutableMap.of(
                "switchid", switchId.toOtsdFormat(),
                "meterId", meterId.toString()
        );
        emitMetric("pen.switch.meters", timestamp, meterId, tags);
    }
}
