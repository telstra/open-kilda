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

import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.stats.model.DummyFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.FlowStatsAndDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.SwitchFlowStats;
import org.openkilda.wfm.topology.stats.service.AnyFlowStatsEntryHandler;
import org.openkilda.wfm.topology.stats.service.FlowEndpointStatsEntryHandler;
import org.openkilda.wfm.topology.stats.service.TimeSeriesMeterEmitter;

import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;


/**
 * The type Flow metric gen bolt.
 */
@Slf4j
public class FlowMetricGenBolt extends MetricGenBolt implements TimeSeriesMeterEmitter {

    public FlowMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        SwitchFlowStats stats = pullValue(input, STATS_FIELD, SwitchFlowStats.class);
        log.debug("dataCache in FlowMetricGenBolt {}", stats);

        long timestamp = pullContext(input).getCreateTime();
        SwitchId switchId = stats.getSwitchId();

        List<FlowStatsAndDescriptor> statsEntries = stats.getStatsEntries();
        Map<Long, Boolean> isAlreadySentCookieMap = Maps.newHashMap();

        for (FlowStatsAndDescriptor statsEntry : statsEntries) {
            handleStatsEntry(statsEntry.getData(), timestamp, switchId, statsEntry.getDescriptor(),
                    useAnyFlowHandlerForCookie(isAlreadySentCookieMap, statsEntry));
            isAlreadySentCookieMap.put(statsEntry.getData().getCookie(), true);
        }
    }

    /**
     * This is a dirty fix, please, suggest an alternative solution if any.
     * Here we have an isAlreadySentCookieMap, that contains the mapping between all the cookies
     * from the stats.getStatsEntries and flags, that represents whether this cookie already been sent to
     * the AnyFlowHandler(this handler generates the raw entries).
     * And the method useAnyFlowHandlerForCookie() that checks whether this cookie has been sent.
     * This fix has been done to avoid sending duplicated RAW metrics for the same cookie and same switchId,
     * but for the different measure points.
     */
    private static boolean useAnyFlowHandlerForCookie(Map<Long, Boolean> isAlreadySentCookieMap,
                                                      FlowStatsAndDescriptor statsEntry) {
        return !isAlreadySentCookieMap.getOrDefault(statsEntry.getData().getCookie(), false);
    }

    private void handleStatsEntry(
            FlowStatsEntry statsEntry, long timestamp, @NonNull SwitchId switchId,
            @Nullable KildaEntryDescriptor descriptor, boolean useAnyFlowHandler) {
        if (descriptor == null) {
            descriptor = new DummyFlowDescriptor(switchId);
        }
        if (useAnyFlowHandler) {
            AnyFlowStatsEntryHandler.apply(this, switchId, timestamp, statsEntry, descriptor);
        }
        FlowEndpointStatsEntryHandler.apply(this, switchId, timestamp, statsEntry, descriptor);
    }
}
