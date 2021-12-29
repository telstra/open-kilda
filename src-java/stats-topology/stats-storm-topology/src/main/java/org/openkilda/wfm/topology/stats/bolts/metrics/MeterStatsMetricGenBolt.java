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

import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.stats.model.DummyMeterDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeterStatsAndDescriptor;
import org.openkilda.wfm.topology.stats.model.SwitchMeterStats;
import org.openkilda.wfm.topology.stats.service.MeterStatsHandler;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import javax.annotation.Nullable;

@Slf4j
public class MeterStatsMetricGenBolt extends MetricGenBolt {
    public MeterStatsMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        SwitchMeterStats stats = pullValue(input, STATS_FIELD, SwitchMeterStats.class);
        log.debug("Received meter statistics: {}.", stats);

        long timestamp = getCommandContext().getCreateTime();
        SwitchId switchId = stats.getSwitchId();
        for (MeterStatsAndDescriptor entry : stats.getStatsEntries()) {
            handleStatsData(entry.getData(), timestamp, switchId, entry.getDescriptor());
        }
    }

    private void handleStatsData(
            MeterStatsEntry meterStats, Long timestamp, SwitchId switchId, @Nullable KildaEntryDescriptor descriptor) {
        if (descriptor == null) {
            descriptor = new DummyMeterDescriptor(switchId);
        }
        MeterStatsHandler.apply(this, switchId, timestamp, meterStats, descriptor);
    }
}
