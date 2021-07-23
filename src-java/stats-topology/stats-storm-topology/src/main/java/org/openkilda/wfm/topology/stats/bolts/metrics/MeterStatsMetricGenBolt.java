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

import static org.openkilda.model.MeterId.isMeterIdOfDefaultRule;
import static org.openkilda.model.cookie.Cookie.createCookieForDefaultRule;
import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;
import static org.openkilda.wfm.topology.stats.bolts.FlowCacheBolt.STATS_CACHED_METER_FIELD;

import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.stats.model.FlowCacheEntry;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

@Slf4j
public class MeterStatsMetricGenBolt extends MetricGenBolt {
    public static final String UNKNOWN_STATS_VALUE = "unknown";

    public MeterStatsMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        MeterStatsData data = (MeterStatsData) input.getValueByField(STATS_FIELD);

        log.debug("Received meter statistics: {}.", data);

        @SuppressWarnings("unchecked")
        Map<MeterCacheKey, FlowCacheEntry> meterCache =
                (Map<MeterCacheKey, FlowCacheEntry>) input.getValueByField(STATS_CACHED_METER_FIELD);

        long timestamp = getCommandContext().getCreateTime();

        SwitchId switchId = data.getSwitchId();
        for (MeterStatsEntry entry : data.getStats()) {
            @Nullable FlowCacheEntry flowEntry = meterCache.get(new MeterCacheKey(switchId, entry.getMeterId()));
            emit(entry, timestamp, switchId, flowEntry);
        }
    }

    private void emit(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId,
                      @Nullable FlowCacheEntry cacheEntry) {
        try {
            if (isMeterIdOfDefaultRule(meterStats.getMeterId())) {
                emitDefaultRuleMeterStats(meterStats, timestamp, switchId);
            } else {
                emitFlowMeterStats(meterStats, timestamp, switchId, cacheEntry);
            }
        } catch (FlowCookieException e) {
            log.warn("Unknown flow direction for flow '{}' on switch '{}'. Message: {}",
                    cacheEntry.getFlowId(), switchId, e.getMessage());
        }
    }

    private void emitDefaultRuleMeterStats(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId) {
        Map<String, String> tags = createCommonTags(switchId, meterStats.getMeterId());
        tags.put("cookieHex", createCookieForDefaultRule(meterStats.getMeterId()).toString());

        emitMetric("switch.flow.system.meter.packets", timestamp, meterStats.getPacketsInCount(), tags);
        emitMetric("switch.flow.system.meter.bytes", timestamp, meterStats.getByteInCount(), tags);
        emitMetric("switch.flow.system.meter.bits", timestamp, meterStats.getByteInCount() * 8, tags);
    }

    private void emitFlowMeterStats(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId,
                                    @Nullable FlowCacheEntry cacheEntry) throws FlowCookieException {
        String direction = UNKNOWN_STATS_VALUE;
        String flowId = UNKNOWN_STATS_VALUE;
        String cookie = UNKNOWN_STATS_VALUE;

        if (cacheEntry == null) {
            if (log.isDebugEnabled()) {
                log.debug("Missed cache for switch '{}' meterId '{}'", switchId, meterStats.getMeterId());
            }
        } else {
            direction = FlowDirectionHelper.findDirection(cacheEntry.getCookie()).name().toLowerCase();
            flowId = cacheEntry.getFlowId();
            cookie = Long.toString(cacheEntry.getCookie());
        }

        Map<String, String> tags = createCommonTags(switchId, meterStats.getMeterId());

        tags.put("direction", direction);
        tags.put("flowid", flowId);
        tags.put("cookie", cookie);

        emitMetric("flow.meter.packets", timestamp, meterStats.getPacketsInCount(), tags);
        emitMetric("flow.meter.bytes", timestamp, meterStats.getByteInCount(), tags);
        emitMetric("flow.meter.bits", timestamp, meterStats.getByteInCount() * 8, tags);
    }

    private Map<String, String> createCommonTags(SwitchId switchId, long meterId) {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("meterid", String.valueOf(meterId));
        return tags;
    }
}
