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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.model.Cookie.isDefaultRule;
import static org.openkilda.wfm.topology.AbstractTopology.fieldMessage;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.wfm.topology.stats.StatsStreamType;
import org.openkilda.wfm.topology.stats.StatsTopology;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpeakerBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerBolt.class);
    private static final String PORT_STATS_STREAM = StatsStreamType.PORT_STATS.toString();
    private static final String METER_CFG_STATS_STREAM = StatsStreamType.METER_CONFIG_STATS.toString();
    private static final String FLOW_STATS_STREAM = StatsStreamType.FLOW_STATS.toString();
    private static final String SYSTEM_RULES_STATS_STREAM = StatsStreamType.SYSTEM_RULE_STATS.toString();

    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        logger.debug("Ingoing tuple: {}", tuple);
        String request = tuple.getString(0);
        try {
            Message stats = Utils.MAPPER.readValue(request, Message.class);
            if (!Destination.WFM_STATS.equals(stats.getDestination()) || !(stats instanceof InfoMessage)) {
                return;
            }
            InfoMessage message = (InfoMessage) stats;
            final InfoData data = message.getData();
            if (data instanceof PortStatsData) {
                logger.debug("Port stats message: {}", new Values(request));
                outputCollector.emit(PORT_STATS_STREAM, tuple, new Values(message));
            } else if (data instanceof MeterConfigStatsData) {
                logger.debug("Meter config stats message: {}", new Values(request));
                outputCollector.emit(METER_CFG_STATS_STREAM, tuple, new Values(message));
            } else if (data instanceof FlowStatsData) {
                logger.debug("Flow stats message: {}", new Values(request));
                ImmutablePair<FlowStatsData, FlowStatsData> splitData =
                        splitSystemRuleStatsAndFlowStats((FlowStatsData) data);

                outputCollector.emit(SYSTEM_RULES_STATS_STREAM, tuple,
                        new Values(splitData.getKey(), message.getTimestamp()));
                outputCollector.emit(FLOW_STATS_STREAM, tuple,
                        new Values(splitData.getValue(), message.getTimestamp()));
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message={}", request, exception);
        } finally {
            outputCollector.ack(tuple);
            logger.debug("Message ack: {}", request);
        }
    }

    private ImmutablePair<FlowStatsData, FlowStatsData> splitSystemRuleStatsAndFlowStats(FlowStatsData data) {
        List<FlowStatsEntry> systemRuleEntries = new ArrayList<>();
        List<FlowStatsEntry> flowEntries = new ArrayList<>();

        for (FlowStatsEntry entry : data.getStats()) {
            if (isDefaultRule(entry.getCookie())) {
                systemRuleEntries.add(entry);
            } else {
                flowEntries.add(entry);
            }
        }
        FlowStatsData systemRuleStats = new FlowStatsData(data.getSwitchId(), systemRuleEntries);
        FlowStatsData flowStats = new FlowStatsData(data.getSwitchId(), flowEntries);

        return new ImmutablePair<>(systemRuleStats, flowStats);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(PORT_STATS_STREAM, fieldMessage);
        outputFieldsDeclarer.declareStream(METER_CFG_STATS_STREAM, fieldMessage);
        outputFieldsDeclarer.declareStream(FLOW_STATS_STREAM, StatsTopology.flowStatsFields);
        outputFieldsDeclarer.declareStream(SYSTEM_RULES_STATS_STREAM, StatsTopology.flowStatsFields);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
