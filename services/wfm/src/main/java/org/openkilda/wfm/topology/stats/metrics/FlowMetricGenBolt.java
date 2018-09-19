/* Copyright 2017 Telstra Open Source
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
import static org.openkilda.wfm.topology.stats.bolts.CacheBolt.CACHE_FIELD;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.FlowCookieException;
import org.openkilda.wfm.topology.stats.FlowDirectionHelper;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.StatsStreamType;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The type Flow metric gen bolt.
 */
public class FlowMetricGenBolt extends MetricGenBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowMetricGenBolt.class);


    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        Map<Long, CacheFlowEntry> dataCache =
                (Map<Long, CacheFlowEntry>) input.getValueByField(CACHE_FIELD);

        LOGGER.debug("dataCache in FlowMetricGenBolt {}", dataCache);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        LOGGER.debug("Flow stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId,
                StatsStreamType.valueOf(input.getSourceStreamId()));
        FlowStatsData data = (FlowStatsData) message.getData();
        long timestamp = message.getTimestamp();
        SwitchId switchId = data.getSwitchId();

        try {
            for (FlowStatsReply reply : data.getStats()) {
                for (FlowStatsEntry entry : reply.getEntries()) {
                    @Nullable CacheFlowEntry flowEntry = dataCache.get(entry.getCookie());
                    emit(entry, timestamp, switchId, flowEntry);
                }
            }
            collector.ack(input);
        } catch (ServiceUnavailableException e) {
            LOGGER.error("Error process: {}", input.toString(), e);
            collector.ack(input); // If we can't connect to Neo then don't know if valid input,
            // but if NEO is down puts a loop to kafka, so fail the request.
        } catch (Exception e) {
            collector.ack(input); // We tried, no need to try again
        }
    }

    private void emit(FlowStatsEntry entry, long timestamp, @Nonnull SwitchId switchId,
                      @Nullable CacheFlowEntry flowEntry) throws Exception {
        String flowId = "unknown";
        if (flowEntry != null) {
            flowId = flowEntry.getFlowId();
        } else {
            LOGGER.warn("missed cache for sw {} cookie {}", switchId, entry.getCookie());
        }

        emitAnySwitchMetrics(entry, timestamp, switchId, flowId);

        if (flowEntry != null) {
            Map<String, String> flowTags = makeFlowTags(entry, flowEntry.getFlowId());

            boolean isMatch = false;
            if (switchId.toOtsdFormat().equals(flowEntry.getIngressSwitch())) {
                emitIngressMetrics(entry, timestamp, flowTags);
                isMatch = true;
            }
            if (switchId.toOtsdFormat().equals(flowEntry.getEgressSwitch())) {
                emitEgressMetrics(entry, timestamp, flowTags);
                isMatch = true;
            }

            if (!isMatch && LOGGER.isDebugEnabled()) {
                LOGGER.debug("FlowStatsEntry with cookie {} and flow {} is not ingress not egress bc switch {} "
                                + "is not any of {}, {}", entry.getCookie(), flowId, switchId,
                        flowEntry.getIngressSwitch(), flowEntry.getEgressSwitch());
            }
        }
    }

    private void emitAnySwitchMetrics(FlowStatsEntry entry, long timestamp, SwitchId switchId, String flowId)
            throws JsonEncodeException, FlowCookieException {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId.toOtsdFormat());
        tags.put("cookie", String.valueOf(entry.getCookie()));
        tags.put("tableid", String.valueOf(entry.getTableId()));
        tags.put("flowid", flowId);
        tags.put("direction", FlowDirectionHelper.findDirection(entry.getCookie()).name().toLowerCase());

        collector.emit(tuple("pen.flow.raw.packets", timestamp, entry.getPacketCount(), tags));
        collector.emit(tuple("pen.flow.raw.bytes", timestamp, entry.getByteCount(), tags));
        collector.emit(tuple("pen.flow.raw.bits", timestamp, entry.getByteCount() * 8, tags));
    }

    private void emitIngressMetrics(FlowStatsEntry entry, long timestamp, Map<String, String> tags)
            throws JsonEncodeException {
        collector.emit(tuple("pen.flow.ingress.packets", timestamp, entry.getPacketCount(), tags));
        collector.emit(tuple("pen.flow.ingress.bytes", timestamp, entry.getByteCount(), tags));
        collector.emit(tuple("pen.flow.ingress.bits", timestamp, entry.getByteCount() * 8, tags));
    }

    private void emitEgressMetrics(FlowStatsEntry entry, long timestamp, Map<String, String> tags)
            throws JsonEncodeException {
        collector.emit(tuple("pen.flow.packets", timestamp, entry.getPacketCount(), tags));
        collector.emit(tuple("pen.flow.bytes", timestamp, entry.getByteCount(), tags));
        collector.emit(tuple("pen.flow.bits", timestamp, entry.getByteCount() * 8, tags));
    }

    private Map<String, String> makeFlowTags(FlowStatsEntry entry, String flowId) throws FlowCookieException {
        Map<String, String> tags = new HashMap<>();
        tags.put("flowid", flowId);
        tags.put("direction", FlowDirectionHelper.findDirection(entry.getCookie()).name().toLowerCase());

        return tags;
    }
}
