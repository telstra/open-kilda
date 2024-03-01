/* Copyright 2023 Telstra Open Source
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

import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.stats.model.DummyGroupDescriptor;
import org.openkilda.wfm.topology.stats.model.GroupStatsAndDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.SwitchGroupStats;
import org.openkilda.wfm.topology.stats.service.GroupStatsHandler;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class GroupStatsMetricGenBolt extends MetricGenBolt {
    public GroupStatsMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        SwitchGroupStats stats = pullValue(input, STATS_FIELD, SwitchGroupStats.class);
        log.debug("Received group statistics: {}.", stats);

        long timestamp = getCommandContext().getCreateTime();
        SwitchId switchId = stats.getSwitchId();
        for (GroupStatsAndDescriptor entry : stats.getStatsEntries()) {
            handleStatsData(entry.getData(), timestamp, switchId, entry.getDescriptor());
        }
    }

    private void handleStatsData(
            GroupStatsEntry groupStats, Long timestamp, SwitchId switchId, @Nullable KildaEntryDescriptor descriptor) {
        if (descriptor == null) {
            descriptor = new DummyGroupDescriptor(switchId);
        }
        GroupStatsHandler.apply(this, switchId, timestamp, groupStats, descriptor);
    }
}
