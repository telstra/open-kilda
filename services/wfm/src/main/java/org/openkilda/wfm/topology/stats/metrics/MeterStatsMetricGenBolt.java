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

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.openkilda.wfm.topology.stats.bolts.CacheBolt.METER_CACHE_FIELD;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsReply;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.topology.stats.FlowByMeterCacheEntry;
import org.openkilda.wfm.topology.stats.FlowCookieException;
import org.openkilda.wfm.topology.stats.FlowDirectionHelper;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.StatsStreamType;

import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class MeterStatsMetricGenBolt extends MetricGenBolt {
    private static final Logger logger = LoggerFactory.getLogger(MeterStatsMetricGenBolt.class);
    private static final long DEFAULT_RULES_COOKIE_MASK = 0x8000000000000000L;
    private static final long[] DEFAULT_RULE_METER_IDS = new long[]{2L, 3L};
    private static final String UNKNOWN_FLOW = "unknown_flow";
    private static final String UNKNOWN_COOKIE = "unknown_cookie";
    private static final String UNKNOWN_DIRECTION = "unknown_direction";

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        Map<Long, FlowByMeterCacheEntry> flowCache =
                (Map<Long, FlowByMeterCacheEntry>) input.getValueByField(METER_CACHE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        logger.debug("Meter stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId,
                StatsStreamType.valueOf(input.getSourceStreamId()));
        MeterStatsData data = (MeterStatsData) message.getData();
        long timestamp = message.getTimestamp();

        try {
            SwitchId switchId = data.getSwitchId();
            for (MeterStatsReply reply : data.getStats()) {
                for (MeterStatsEntry entry : reply.getEntries()) {
                    @Nullable FlowByMeterCacheEntry flowEntry = flowCache.get(entry.getMeterId());
                    emit(entry, timestamp, switchId, flowEntry);
                }
            }
        } finally {
            collector.ack(input);
        }
    }

    private void emit(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId,
                      @Nullable FlowByMeterCacheEntry cacheEntry) {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("switchid", switchId.toOtsdFormat());
            tags.put("meterid", String.valueOf(meterStats.getMeterId()));

            String packetsMetric;
            String bytesMetric;
            String bitsMetric;

            if (isDefaultRule(meterStats.getMeterId())) {
                packetsMetric = "pen.switch.flow.system.meter.packets";
                bytesMetric = "pen.switch.flow.system.meter.bytes";
                bitsMetric = "pen.switch.flow.system.meter.bits";

                tags.put("cookie", String.format("%X", meterStats.getMeterId() | DEFAULT_RULES_COOKIE_MASK));

            } else {
                packetsMetric = "pen.flow.meter.packets";
                bytesMetric = "pen.flow.meter.bytes";
                bitsMetric = "pen.flow.meter.bits";

                addFlowTags(tags, meterStats.getMeterId(), switchId, cacheEntry);
            }

            collector.emit(tuple(packetsMetric, timestamp, meterStats.getPacketsInCount(), tags));
            collector.emit(tuple(bytesMetric, timestamp, meterStats.getByteInCount(), tags));
            collector.emit(tuple(bitsMetric, timestamp, meterStats.getByteInCount() * 8, tags));
        } catch (JsonEncodeException e) {
            logger.error("Error during serialization of datapoint", e);
        }
    }

    private void addFlowTags(Map<String, String> tags, Long meterId, SwitchId switchId,
                             @Nullable FlowByMeterCacheEntry cacheEntry) {
        String cookie;
        String direction;
        String flowId;

        if (cacheEntry == null) {
            logger.warn("Missed cache for switch '{}' meterId '{}'", switchId, meterId);
            flowId = UNKNOWN_FLOW;
            cookie = UNKNOWN_COOKIE;
            direction = UNKNOWN_DIRECTION;
        } else {
            flowId = cacheEntry.getFlowId();
            cookie = cacheEntry.getCookie().toString();
            direction = getDirection(cacheEntry.getCookie(), switchId);
        }

        tags.put("flowid", flowId);
        tags.put("cookie", cookie);
        tags.put("direction", direction);
    }

    private boolean isDefaultRule(long cookie) {
        for (long defaultRuleMeterId : DEFAULT_RULE_METER_IDS) {
            if (cookie == defaultRuleMeterId) {
                return true;
            }
        }
        return false;
    }

    private String getDirection(long cookie, SwitchId switchId) {
        try {
            return FlowDirectionHelper.findDirection(cookie).name().toLowerCase();
        } catch (FlowCookieException e) {
            logger.warn("Unknown flow direction on switch '{}': {}", switchId, e.getMessage());
            return UNKNOWN_DIRECTION;
        }
    }
}
