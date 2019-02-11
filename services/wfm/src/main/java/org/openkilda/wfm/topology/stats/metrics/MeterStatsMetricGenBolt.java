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

package org.openkilda.wfm.topology.stats.metrics;

import static org.openkilda.messaging.Utils.TIMESTAMP;
import static org.openkilda.model.Cookie.createCookieForDefaultRule;
import static org.openkilda.model.MeterId.isMeterIdOfDefaultRule;
import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;
import static org.openkilda.wfm.topology.stats.bolts.CacheBolt.METER_CACHE_FIELD;

import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.FlowCookieException;
import org.openkilda.wfm.topology.stats.FlowDirectionHelper;
import org.openkilda.wfm.topology.stats.MeterCacheKey;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

@Slf4j
public class MeterStatsMetricGenBolt extends MetricGenBolt {

    public MeterStatsMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        MeterStatsData data = (MeterStatsData) input.getValueByField(STATS_FIELD);

        log.debug("Received meter statistics: {}.", data);

        @SuppressWarnings("unchecked")
        Map<MeterCacheKey, CacheFlowEntry> meterCache =
                (Map<MeterCacheKey, CacheFlowEntry>) input.getValueByField(METER_CACHE_FIELD);

        long timestamp = input.getLongByField(TIMESTAMP);

        SwitchId switchId = data.getSwitchId();
        for (MeterStatsEntry entry : data.getStats()) {
            @Nullable CacheFlowEntry flowEntry = meterCache.get(new MeterCacheKey(switchId, entry.getMeterId()));
            emit(entry, timestamp, switchId, flowEntry);
        }
    }

    private void emit(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId,
                      @Nullable CacheFlowEntry cacheEntry) {
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
                                    @Nullable CacheFlowEntry cacheEntry) throws FlowCookieException {
        if (cacheEntry == null) {
            log.warn("Missed cache for switch '{}' meterId '{}'", switchId, meterStats.getMeterId());
            return;
        }

        Map<String, String> tags = createCommonTags(switchId, meterStats.getMeterId());

        String direction = FlowDirectionHelper.findDirection(cacheEntry.getCookie()).name().toLowerCase();

        tags.put("direction", direction);
        tags.put("flowid", cacheEntry.getFlowId());
        tags.put("cookie", cacheEntry.getCookie().toString());

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
