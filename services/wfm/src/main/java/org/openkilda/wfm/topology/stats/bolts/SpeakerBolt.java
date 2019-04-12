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

import static org.openkilda.messaging.Utils.TIMESTAMP;
import static org.openkilda.model.Cookie.isDefaultRule;
import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.stats.StatsStreamType;
import org.openkilda.wfm.topology.stats.StatsTopology;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpeakerBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerBolt.class);
    private static final String PORT_STATS_STREAM = StatsStreamType.PORT_STATS.toString();
    private static final String METER_CFG_STATS_STREAM = StatsStreamType.METER_CONFIG_STATS.toString();
    private static final String CACHE_STREAM = StatsStreamType.CACHE_DATA.toString();
    private static final String SYSTEM_RULES_STATS_STREAM = StatsStreamType.SYSTEM_RULE_STATS.toString();

    private transient OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        logger.debug("Ingoing tuple: {}", tuple);

        Message message = null;
        try {
            message = (Message) tuple.getValueByField(MessageTranslator.FIELD_ID_PAYLOAD);
            if (!(message instanceof InfoMessage)) {
                return;
            }
            CommandContext commandContext = (CommandContext) tuple.getValueByField(FIELD_ID_CONTEXT);
            InfoMessage infoMessage = (InfoMessage) message;
            final InfoData data = infoMessage.getData();
            if (data instanceof PortStatsData) {
                logger.debug("Port stats message: {}", infoMessage);
                outputCollector.emit(PORT_STATS_STREAM, tuple, new Values(infoMessage, commandContext));
            } else if (data instanceof MeterConfigStatsData) {
                logger.debug("Meter config stats message: {}", infoMessage);
                outputCollector.emit(METER_CFG_STATS_STREAM, tuple, new Values(infoMessage, commandContext));
            } else if (data instanceof MeterStatsData) {
                logger.debug("Meter stats message: {}", infoMessage);
                outputCollector.emit(CACHE_STREAM, tuple, new Values(data, infoMessage.getTimestamp(), commandContext));
            } else if (data instanceof FlowStatsData) {
                logger.debug("Flow stats message: {}", infoMessage);
                ImmutablePair<FlowStatsData, FlowStatsData> splitData =
                        splitSystemRuleStatsAndFlowStats((FlowStatsData) data);

                outputCollector.emit(SYSTEM_RULES_STATS_STREAM, tuple,
                        new Values(splitData.getKey(), infoMessage.getTimestamp(), commandContext));
                outputCollector.emit(CACHE_STREAM, tuple,
                        new Values(splitData.getValue(), infoMessage.getTimestamp(), commandContext));
            }
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);
            logger.debug("Message ack: {}", message);
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
        Fields fields = new Fields(MessageTranslator.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);
        outputFieldsDeclarer.declareStream(PORT_STATS_STREAM, fields);
        outputFieldsDeclarer.declareStream(METER_CFG_STATS_STREAM, fields);

        Fields statsFields = new Fields(StatsTopology.STATS_FIELD, TIMESTAMP, FIELD_ID_CONTEXT);
        outputFieldsDeclarer.declareStream(CACHE_STREAM, statsFields);
        outputFieldsDeclarer.declareStream(SYSTEM_RULES_STATS_STREAM, statsFields);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
