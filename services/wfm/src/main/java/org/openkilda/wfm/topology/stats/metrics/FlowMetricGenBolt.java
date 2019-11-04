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

import org.openkilda.exception.CookieTypeMismatchException;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.IngressSegmentCookie;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.bolts.CacheBolt;
import org.openkilda.wfm.topology.stats.model.StatsFlowBatch;
import org.openkilda.wfm.topology.stats.model.StatsFlowEntry;

import com.google.common.collect.ImmutableSet;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The type Flow metric gen bolt.
 */
public class FlowMetricGenBolt extends MetricGenBolt {

    private static Set<Cookie.CookieType> flowSegmentTypes = ImmutableSet.of(
            Cookie.CookieType.FLOW_SEGMENT,
            Cookie.CookieType.INGRESS_SEGMENT);

    public FlowMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        StatsFlowBatch batch = pullValue(input, CacheBolt.FIELD_ID_PAYLOAD, StatsFlowBatch.class);

        SwitchId switchId = batch.getSwitchId();
        long timestamp = getCommandContext().getCreateTime();
        for (StatsFlowEntry entry : batch.getEntries()) {
            handleStatsEntry(switchId, timestamp, entry);
        }
    }

    private void handleStatsEntry(SwitchId switchId, long timestamp, StatsFlowEntry entry) {
        final CacheFlowEntry flowData = entry.getFlowData();
        final FlowStatsEntry stats = entry.getStatsData();

        final Cookie cookie = new Cookie(stats.getCookie());
        final Cookie.CookieType cookieType = cookie.getType();

        String flowId = "unknown";
        if (flowData != null) {
            flowId = flowData.getFlowId();
        } else {
            reportMissingFlowData(switchId, cookie, cookieType);
        }

        emitAnySwitchMetrics(switchId, timestamp, flowId, cookie.getFlowPathDirection(), stats);

        if (flowData != null) {
            emitFlowRelatedMetrics(switchId, timestamp, flowData, stats, cookie);
        }
    }

    private void emitAnySwitchMetrics(
            SwitchId switchId, long timestamp, String flowId, FlowPathDirection direction, FlowStatsEntry entry) {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("cookie", String.valueOf(entry.getCookie()));
        tags.put("tableid", String.valueOf(entry.getTableId()));
        tags.put("outPort", String.valueOf(entry.getOutPort()));
        tags.put("inPort", String.valueOf(entry.getInPort()));
        makeFlowIdTag(tags, flowId);
        makeDirectionTag(tags, direction);

        emitMetric("flow.raw.packets", timestamp, entry.getPacketCount(), tags);
        emitMetric("flow.raw.bytes", timestamp, entry.getByteCount(), tags);
        emitMetric("flow.raw.bits", timestamp, entry.getByteCount() * 8, tags);
    }

    private void emitFlowRelatedMetrics(
            SwitchId switchId, long timestamp, CacheFlowEntry flowData, FlowStatsEntry stats, Cookie cookie) {
        Map<String, String> flowTags = makeFlowTags(flowData.getFlowId(), cookie.getFlowPathDirection());

        if (isFlowTrafficForwardingEntry(cookie)) {
            boolean isMatch = false;
            if (switchId.equals(flowData.getIngressSwitch())) {
                emitIngressMetrics(stats, timestamp, flowTags);
                isMatch = true;
            }
            if (switchId.equals(flowData.getEgressSwitch())) {
                emitEgressMetrics(stats, timestamp, flowTags);
                isMatch = true;
            }

            if (!isMatch && log.isDebugEnabled()) {
                log.debug("FlowStatsEntry with cookie {} and flow {} is not ingress not egress bc switch {} "
                                  + "is not any of {}, {}", cookie, flowData.getFlowId(), switchId,
                          flowData.getIngressSwitch(), flowData.getEgressSwitch());
            }
        }
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

    private boolean isFlowTrafficForwardingEntry(Cookie cookie) {
        boolean result;
        try {
            IngressSegmentCookie ingressCookie = IngressSegmentCookie.unpack(cookie);
            result = ingressCookie.isForwarding();
        } catch (CookieTypeMismatchException e) {
            // not ingress cookie
            result = cookie.getType() == Cookie.CookieType.FLOW_SEGMENT;
        }
        return result;
    }

    private Map<String, String> makeFlowTags(String flowId, FlowPathDirection direction) {
        Map<String, String> tags = new HashMap<>();
        makeFlowIdTag(tags, flowId);
        makeDirectionTag(tags, direction);
        return tags;
    }

    private void makeFlowIdTag(Map<String, String> tags, String flowId) {
        tags.put("flowid", flowId);
    }

    private void makeDirectionTag(Map<String, String> tags, FlowPathDirection direction) {
        tags.put("direction", direction.name().toLowerCase());
    }

    private void reportMissingFlowData(SwitchId switchId, Cookie cookie, Cookie.CookieType cookieType) {
        String message = String.format(
                "Missed flow data for stats entry on switch %s with cookie %s (type=%s)",
                switchId, cookie, cookieType);
        if (flowSegmentTypes.contains(cookieType)) {
            log.warn(message);
        } else {
            log.debug(message);
        }
    }
}
