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

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.wfm.topology.stats.FlowDirectionHelper;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.StatsStreamType;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
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
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        FlowStatsData data = (FlowStatsData) message.getData();
        long timestamp = message.getTimestamp();
        String switchId = data.getSwitchId().replaceAll(":", "");

        try {
            for (FlowStatsReply reply : data.getStats()) {
                for (FlowStatsEntry entry : reply.getEntries()) {

                    String flow = "unknown";
                    String dstSwitch = null;
                    if (dataCache.containsKey(entry.getCookie()))
                    {
                        CacheFlowEntry cacheFlowEntry = dataCache.get(entry.getCookie());
                        flow = cacheFlowEntry.getFlowId();
                        dstSwitch = cacheFlowEntry.getDestinationSwitch();
                    }
                    else
                    {
                        LOGGER.warn("missed cache for cookie {}", entry.getCookie());
                    }
                    emit(entry, timestamp, switchId, flow, dstSwitch);
                }
            }
            collector.ack(input);
        } catch (ServiceUnavailableException e) {
            LOGGER.error("Error process: {}", input.toString(), e);
            collector.ack(input); // If we can't connect to Neo then don't know if valid input, but if NEO is down puts a loop to
                                  // kafka, so fail the request.
        } catch (Exception e) {
            collector.ack(input); // We tried, no need to try again
        }
    }

    private void emit(FlowStatsEntry entry, long timestamp, String switchId, String flowId,
            @Nullable String dstSwitchId) throws Exception {
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", switchId);
        tags.put("cookie", String.valueOf(entry.getCookie()));
        tags.put("tableid", String.valueOf(entry.getTableId()));
        tags.put("flowid", flowId);

        collector.emit(tuple("pen.flow.raw.packets", timestamp, entry.getPacketCount(), tags));
        collector.emit(tuple("pen.flow.raw.bytes", timestamp, entry.getByteCount(), tags));
        collector.emit(tuple("pen.flow.raw.bits", timestamp, entry.getByteCount() * 8, tags));

        /**
         * If this is the destination switch for the flow, then add to TSDB for pen.flow.* stats.  This is needed
         * as there is needed to provide simple lookup of flow stats
         **/
        if (isEngressFlow(switchId, dstSwitchId)) {
            tags.remove("cookie");  //Doing this to prevent creation of yet another object
            tags.remove("tableid");
            tags.remove("switchid");
            tags.put("direction", FlowDirectionHelper.findDirection(entry.getCookie()).name().toLowerCase());
            collector.emit(tuple("pen.flow.packets", timestamp, entry.getPacketCount(), tags));
            collector.emit(tuple("pen.flow.bytes", timestamp, entry.getByteCount(), tags));
            collector.emit(tuple("pen.flow.bits", timestamp, entry.getByteCount() * 8, tags));
        }
    }

    private boolean isEngressFlow(String switchId, @Nullable String dstSwitchId) {
        return dstSwitchId != null &&
                switchId.equals(dstSwitchId.replaceAll(":", ""));
    }
}
