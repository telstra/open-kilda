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

package org.openkilda.wfm.topology.flow.bolts;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.TransactionBoltState;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;

import org.apache.storm.shade.org.eclipse.jetty.util.ConcurrentHashSet;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transaction Bolt. Tracks OpenFlow Speaker commands transactions.
 * <p/>
 * ALGORITHM NOTES:
 * <p/>
 * 1. The TOPOLOGY_ENGINE_BOLT should send the flow rules first, otherwise the SPEAKER_BOLT logic won't have anything to
 * clear.
 */
public class TransactionBolt
        extends BaseStatefulBolt<InMemoryKeyValueState<SwitchId, Map<String, Set<Long>>>>
        implements ICtrlBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(TransactionBolt.class);

    public static final String STREAM_ID_CTRL = "ctrl";

    /**
     * Transaction ids state.
     * <p/>
     * FIXME(surabujin) in memory status lead to disaster when system restarts during any transition
     */
    private InMemoryKeyValueState<SwitchId, Map<String, Set<Long>>> transactions;

    private TopologyContext context;
    private OutputCollector outputCollector;

    @Override
    public void execute(Tuple tuple) {

        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }

        logger.trace("States before: {}", transactions);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        Long transactionId = (Long) tuple.getValueByField(Utils.TRANSACTION_ID);
        SwitchId switchId = (SwitchId) tuple.getValueByField(FlowTopology.SWITCH_ID_FIELD);
        String flowId = (String) tuple.getValueByField(Utils.FLOW_ID);
        Object message = tuple.getValueByField(FlowTopology.MESSAGE_FIELD);
        Map<String, Set<Long>> flowTransactions;
        Set<Long> flowTransactionIds;
        Values values = null;

        try {
            logger.debug("Request tuple={}", tuple);

            switch (componentId) {

                case COMMAND_BOLT:
                    logger.info("Transaction from TopologyEngine: switch-id={}, {}={}, {}={}",
                            switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                    flowTransactions = transactions.get(switchId);
                    if (flowTransactions == null) {
                        flowTransactions = new ConcurrentHashMap<>();
                        transactions.put(switchId, flowTransactions);
                    }

                    flowTransactionIds = flowTransactions.get(flowId);
                    if (flowTransactionIds == null) {
                        flowTransactionIds = new ConcurrentHashSet<>();
                        flowTransactions.put(flowId, flowTransactionIds);
                    }

                    if (!flowTransactionIds.add(transactionId)) {
                        throw new RuntimeException(
                                String.format("Transaction adding failure: id %d already exists", transactionId));
                    }

                    logger.info("Set status {}: switch-id={}, {}={}, {}={}", FlowState.IN_PROGRESS,
                            switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                    values = new Values(flowId, FlowState.IN_PROGRESS);
                    outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

                    values = new Values(message);
                    outputCollector.emit(streamId.toString(), tuple, values);
                    break;

                case SPEAKER_BOLT:

                    logger.info("Transaction from Speaker: switch-id={}, {}={}, {}={}",
                            switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                    flowTransactions = transactions.get(switchId);
                    if (flowTransactions != null) {

                        flowTransactionIds = flowTransactions.get(flowId);
                        if (flowTransactionIds != null) {

                            if (flowTransactionIds.remove(transactionId)) {

                                if (flowTransactionIds.isEmpty()) {
                                    //
                                    // All transactions have been removed .. the Flow
                                    // can now be considered "UP"
                                    //
                                    logger.info(
                                            "Flow transaction completed for one switch "
                                                    + "(switch: {}, flow: {}, stream: {})", switchId, flowId, streamId);

                                    values = new Values(flowId, FlowState.UP);
                                    outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

                                    flowTransactions.remove(flowId);
                                } else {
                                    logger.debug("Transaction {} not empty yet, count = {}",
                                            transactionId, flowTransactionIds.size()
                                    );
                                }
                            } else {
                                logger.warn("Transaction removing: transaction id not found");
                            }
                        } else {
                            logger.warn("Transaction removing failure: flow id not found");
                        }
                        if (flowTransactions.isEmpty()) {
                            transactions.delete(switchId);
                        }
                    } else {
                        logger.warn("Transaction removing failure: switch id not found");
                    }
                    break;

                default:
                    logger.debug("Skip undefined message: message={}", tuple);
                    break;
            }
        } catch (RuntimeException exception) {
            logger.error("Set status {}: switch-id={}, {}={}, {}={}",
                    FlowState.DOWN, switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId, exception);

            values = new Values(flowId, FlowState.DOWN);
            outputCollector.emit(StreamType.STATUS.toString(), tuple, values);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);

            logger.debug("Transaction message ack: component={}, stream={}, tuple={}, values={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple, values);
        }

        logger.trace("States after: {}", transactions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<SwitchId, Map<String, Set<Long>>> state) {
        transactions = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), FlowTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), FlowTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), FlowTopology.fieldsFlowIdStatus);
        // FIXME(dbogun): use proper tuple format
        outputFieldsDeclarer.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.context = topologyContext;
        this.outputCollector = outputCollector;
    }

    @Override
    public AbstractDumpState dumpState() {
        Map<SwitchId, Map<String, Set<Long>>> dump = new HashMap<>();
        for (Map.Entry<SwitchId, Map<String, Set<Long>>> item : transactions) {
            dump.put(item.getKey(), item.getValue());
        }
        return new TransactionBoltState(dump);
    }

    @Override
    public String getCtrlStreamId() {
        return STREAM_ID_CTRL;
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(SwitchId switchId) {
        // Not implemented
        return new TransactionBoltState(new HashMap<>());
    }

    @Override
    public TopologyContext getContext() {
        return context;
    }

    @Override
    public OutputCollector getOutput() {
        return outputCollector;
    }
}
