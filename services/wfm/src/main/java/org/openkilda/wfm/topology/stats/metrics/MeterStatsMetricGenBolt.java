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

import static org.openkilda.model.Constants.DEFAULT_RULES_COOKIE_MASK;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.openkilda.wfm.topology.stats.bolts.CacheBolt.METER_CACHE_FIELD;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.topology.stats.FlowByMeterCacheEntry;
import org.openkilda.wfm.topology.stats.FlowCookieException;
import org.openkilda.wfm.topology.stats.FlowDirectionHelper;

import javafx.util.Pair;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class MeterStatsMetricGenBolt extends MetricGenBolt {
    private static final Logger logger = LoggerFactory.getLogger(MeterStatsMetricGenBolt.class);

    @Override
    public void execute(Tuple input) {
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        logger.debug("Received meter statistics: {}.", message.getData());

        Map<Pair<SwitchId, Long>, FlowByMeterCacheEntry> flowCache =
                (Map<Pair<SwitchId, Long>, FlowByMeterCacheEntry>) input.getValueByField(METER_CACHE_FIELD);

        MeterStatsData data = (MeterStatsData) message.getData();
        long timestamp = message.getTimestamp();

        try {
            SwitchId switchId = data.getSwitchId();
            for (MeterStatsEntry entry : data.getStats()) {
                @Nullable FlowByMeterCacheEntry flowEntry = flowCache.get(new Pair<>(switchId, entry.getMeterId()));
                emit(entry, timestamp, switchId, flowEntry);
            }
        } finally {
            collector.ack(input);
        }
    }

    private void emit(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId,
                      @Nullable FlowByMeterCacheEntry cacheEntry) {
        try {
            if (isDefaultRule(meterStats.getMeterId())) {
                emitDefaultRuleMeterStats(meterStats, timestamp, switchId);
            } else {
                emitFlowMeterStats(meterStats, timestamp, switchId, cacheEntry);
            }
        } catch (JsonEncodeException e) {
            logger.error("Error during serialization of datapoint", e);
        }
    }

    private void emitDefaultRuleMeterStats(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId)
            throws JsonEncodeException {
        Map<String, String> tags = createCommonTags(switchId, meterStats.getMeterId());
        tags.put("cookie", String.format("%X", meterStats.getMeterId() | DEFAULT_RULES_COOKIE_MASK));

        collector.emit(tuple("pen.switch.flow.system.meter.packets", timestamp, meterStats.getPacketsInCount(), tags));
        collector.emit(tuple("pen.switch.flow.system.meter.bytes", timestamp, meterStats.getByteInCount(), tags));
        collector.emit(tuple("pen.switch.flow.system.meter.bits", timestamp, meterStats.getByteInCount() * 8, tags));
    }

    private void emitFlowMeterStats(MeterStatsEntry meterStats, Long timestamp, SwitchId switchId,
                                    @Nullable FlowByMeterCacheEntry cacheEntry) throws JsonEncodeException {
        Map<String, String> tags = createCommonTags(switchId, meterStats.getMeterId());

        if (cacheEntry == null) {
            logger.warn("Missed cache for switch '{}' meterId '{}'", switchId, meterStats.getMeterId());
            return;
        }

        String direction;
        try {
            direction = FlowDirectionHelper.findDirection(cacheEntry.getCookie()).name().toLowerCase();
        } catch (FlowCookieException e) {
            logger.warn("Unknown flow direction for flow '{}' with cookie '{}' on switch '{}'. Message: {}",
                    cacheEntry.getFlowId(), cacheEntry.getCookie(), switchId, e.getMessage());
            return;
        }

        tags.put("direction", direction);
        tags.put("flowid", cacheEntry.getFlowId());
        tags.put("cookie", cacheEntry.getCookie().toString());

        collector.emit(tuple("pen.flow.meter.packets", timestamp, meterStats.getPacketsInCount(), tags));
        collector.emit(tuple("pen.flow.meter.bytes", timestamp, meterStats.getByteInCount(), tags));
        collector.emit(tuple("pen.flow.meter.bits", timestamp, meterStats.getByteInCount() * 8, tags));
    }

    private Map<String, String> createCommonTags(SwitchId switchId, long meterId) {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("meterid", String.valueOf(meterId));
        return tags;
    }

    private boolean isDefaultRule(long meterId) {
        return 0 < meterId && meterId < 10;
    }
}
