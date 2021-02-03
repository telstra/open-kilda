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

import static org.openkilda.model.cookie.Cookie.isDefaultRule;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.grpc.GetPacketInOutStatsResponse;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.SwitchTableStatsData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.stats.StatsStreamType;
import org.openkilda.wfm.topology.stats.StatsTopology;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SpeakerBolt extends AbstractBolt {
    public static final String ZOOKEEPER_STREAM = ZkStreams.ZK.toString();

    private static final Logger logger = LoggerFactory.getLogger(SpeakerBolt.class);
    private static final String PORT_STATS_STREAM = StatsStreamType.PORT_STATS.toString();
    private static final String METER_CFG_STATS_STREAM = StatsStreamType.METER_CONFIG_STATS.toString();
    private static final String CACHE_STREAM = StatsStreamType.CACHE_DATA.toString();
    private static final String SYSTEM_RULES_STATS_STREAM = StatsStreamType.SYSTEM_RULE_STATS.toString();
    private static final String TABLE_STATS_STREAM = StatsStreamType.TABLE_STATS.toString();
    private static final String PACKET_IN_OUT_STATS_STREAM = StatsStreamType.PACKET_IN_OUT_STATS.toString();

    public SpeakerBolt(String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
    }

    @Override
    protected void handleInput(Tuple tuple) throws Exception {
        logger.debug("Ingoing tuple: {}", tuple);

        if (active) {
            Message message = pullValue(tuple, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Message.class);
            if (!(message instanceof InfoMessage)) {
                return;
            }
            InfoMessage infoMessage = (InfoMessage) message;
            final InfoData data = infoMessage.getData();
            if (data instanceof PortStatsData) {
                logger.debug("Port stats message: {}", infoMessage);
                emitWithContext(PORT_STATS_STREAM, tuple, new Values(infoMessage));
            } else if (data instanceof MeterConfigStatsData) {
                logger.debug("Meter config stats message: {}", infoMessage);
                emitWithContext(METER_CFG_STATS_STREAM, tuple, new Values(infoMessage));
            } else if (data instanceof MeterStatsData) {
                logger.debug("Meter stats message: {}", infoMessage);
                emitWithContext(CACHE_STREAM, tuple, new Values(data));
            } else if (data instanceof FlowStatsData) {
                logger.debug("Flow stats message: {}", infoMessage);
                ImmutablePair<FlowStatsData, FlowStatsData> splitData =
                        splitSystemRuleStatsAndFlowStats((FlowStatsData) data);

                emitWithContext(SYSTEM_RULES_STATS_STREAM, tuple, new Values(splitData.getKey()));
                emitWithContext(CACHE_STREAM, tuple, new Values(splitData.getValue()));
            } else if (data instanceof SwitchTableStatsData) {
                logger.debug("Table stats message: {}", infoMessage);
                emitWithContext(TABLE_STATS_STREAM, tuple, new Values(data));
            } else if (data instanceof GetPacketInOutStatsResponse) {
                logger.debug("Packet in out stats message: {}", infoMessage);
                emitWithContext(PACKET_IN_OUT_STATS_STREAM, tuple, new Values(data));
            } else {
                //FIXME (ncherevko): we might receive few unexpected messages here,
                // need to fix it and uncomment below line
                //unhandledInput(tuple);
            }
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
        Fields fields = new Fields(MessageKafkaTranslator.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);
        outputFieldsDeclarer.declareStream(PORT_STATS_STREAM, fields);
        outputFieldsDeclarer.declareStream(METER_CFG_STATS_STREAM, fields);

        Fields statsFields = new Fields(StatsTopology.STATS_FIELD, FIELD_ID_CONTEXT);
        outputFieldsDeclarer.declareStream(CACHE_STREAM, statsFields);
        outputFieldsDeclarer.declareStream(SYSTEM_RULES_STATS_STREAM, statsFields);
        outputFieldsDeclarer.declareStream(TABLE_STATS_STREAM, statsFields);
        outputFieldsDeclarer.declareStream(PACKET_IN_OUT_STATS_STREAM, statsFields);
        outputFieldsDeclarer.declareStream(ZOOKEEPER_STREAM, new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
