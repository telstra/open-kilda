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

import org.openkilda.messaging.info.stats.SwitchTableStatsData;
import org.openkilda.messaging.info.stats.TableStatsEntry;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class TableStatsMetricGenBolt extends MetricGenBolt {

    public TableStatsMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        SwitchTableStatsData statsData = pullValue(input, STATS_FIELD, SwitchTableStatsData.class);
        for (TableStatsEntry entry : statsData.getTableStatsEntries()) {
            emit(statsData.getSwitchId(), entry);
        }
    }

    private void emit(SwitchId switchId, TableStatsEntry entry) {
        long timestamp = getCommandContext().getCreateTime();
        Map<String, String> tags = ImmutableMap.of(
                "switchid", switchId.toOtsdFormat(),
                "tableid", String.valueOf(entry.getTableId())
        );

        emitMetric("switch.table.active", timestamp, entry.getActiveEntries(), tags);
        emitMetric("switch.table.lookup", timestamp, entry.getLookupCount(), tags);
        emitMetric("switch.table.matched", timestamp, entry.getMatchedCount(), tags);
        emitMetric("switch.table.missed", timestamp, entry.getLookupCount() - entry.getMatchedCount(), tags);
    }
}
