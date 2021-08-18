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
import static org.openkilda.wfm.topology.stats.bolts.FlowCacheBolt.STATS_CACHED_COOKIE_FIELD;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.EGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.ONE_SWITCH;

import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper.Direction;
import org.openkilda.wfm.topology.stats.model.FlowCacheEntry;

import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The type Flow metric gen bolt.
 */
public class FlowMetricGenBolt extends MetricGenBolt {

    public FlowMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        Map<Long, FlowCacheEntry> dataCache =
                (Map<Long, FlowCacheEntry>) input.getValueByField(STATS_CACHED_COOKIE_FIELD);
        log.debug("dataCache in FlowMetricGenBolt {}", dataCache);

        FlowStatsData data = (FlowStatsData) input.getValueByField(STATS_FIELD);
        long timestamp = pullContext(input).getCreateTime();
        SwitchId switchId = data.getSwitchId();

        for (FlowStatsEntry entry : data.getStats()) {
            @Nullable FlowCacheEntry flowEntry = dataCache.get(entry.getCookie());
            emit(entry, timestamp, switchId, flowEntry);
        }
    }

    private void emit(FlowStatsEntry entry, long timestamp, @Nonnull SwitchId switchId,
                      @Nullable FlowCacheEntry flowEntry) throws FlowCookieException {
        boolean isFlowSegmentEntry = new Cookie(entry.getCookie()).getType() == CookieType.SERVICE_OR_FLOW_SEGMENT
                && !new FlowSegmentCookie(entry.getCookie()).isLooped();

        String flowId = "unknown";
        if (flowEntry != null) {
            flowId = flowEntry.getFlowId();
        } else {
            if (isFlowSegmentEntry) {
                log.warn("Missed cache for switch {} cookie {}", switchId, entry.getCookie());
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Missed cache for non flow cookie {} on switch {}", entry.getCookie(), switchId);
                }
            }
        }

        emitAnySwitchMetrics(entry, timestamp, switchId, flowId);

        if (flowEntry != null) {
            Map<String, String> flowTags = makeFlowTags(entry, flowEntry.getFlowId());

            if (isFlowSegmentEntry) {
                if (flowEntry.getMeasurePoint() == INGRESS || flowEntry.getMeasurePoint() == ONE_SWITCH) {
                    emitIngressMetrics(entry, timestamp, flowTags);
                }
                if (flowEntry.getMeasurePoint() == EGRESS || flowEntry.getMeasurePoint() == ONE_SWITCH) {
                    emitEgressMetrics(entry, timestamp, flowTags);
                }
            }
        }
    }

    private void emitAnySwitchMetrics(FlowStatsEntry entry, long timestamp, SwitchId switchId, String flowId) {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("cookie", String.valueOf(entry.getCookie()));
        tags.put("tableid", String.valueOf(entry.getTableId()));
        tags.put("outPort", String.valueOf(entry.getOutPort()));
        tags.put("inPort", String.valueOf(entry.getInPort()));
        tags.put("flowid", flowId);
        tags.put("direction", FlowDirectionHelper.findDirectionSafe(entry.getCookie())
                .orElse(Direction.UNKNOWN)
                .name().toLowerCase());
        CookieType cookieType = new Cookie(entry.getCookie()).getType();
        tags.put("type", cookieType.name().toLowerCase());

        emitMetric("flow.raw.packets", timestamp, entry.getPacketCount(), tags);
        emitMetric("flow.raw.bytes", timestamp, entry.getByteCount(), tags);
        emitMetric("flow.raw.bits", timestamp, entry.getByteCount() * 8, tags);
    }

    private void emitIngressMetrics(FlowStatsEntry entry, long timestamp, Map<String, String> tags) {
        emitMetric("flow.ingress.packets", timestamp, entry.getPacketCount(), tags);
        emitMetric("flow.ingress.bytes", timestamp, entry.getByteCount(), tags);
        emitMetric("flow.ingress.bits", timestamp, entry.getByteCount() * 8, tags);
    }

    private void emitEgressMetrics(FlowStatsEntry entry, long timestamp, Map<String, String> tags) {
        emitMetric("flow.packets", timestamp, entry.getPacketCount(), tags);
        emitMetric("flow.bytes", timestamp, entry.getByteCount(), tags);
        emitMetric("flow.bits", timestamp, entry.getByteCount() * 8, tags);
    }

    private Map<String, String> makeFlowTags(FlowStatsEntry entry, String flowId) throws FlowCookieException {
        Map<String, String> tags = new HashMap<>();
        tags.put("flowid", flowId);
        tags.put("direction", FlowDirectionHelper.findDirection(entry.getCookie()).name().toLowerCase());

        return tags;
    }
}
