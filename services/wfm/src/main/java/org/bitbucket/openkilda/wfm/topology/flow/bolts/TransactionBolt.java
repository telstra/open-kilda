package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;
import org.bitbucket.openkilda.wfm.topology.flow.ComponentType;
import org.bitbucket.openkilda.wfm.topology.flow.FlowTopology;
import org.bitbucket.openkilda.wfm.topology.flow.StreamType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.shade.org.eclipse.jetty.util.ConcurrentHashSet;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transaction Bolt. Tracks OpenFlow Speaker commands transactions.
 */
public class TransactionBolt extends BaseStatefulBolt<InMemoryKeyValueState<String, Map<String, Set<Long>>>> {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(TransactionBolt.class);

    /**
     * Transaction ids state.
     */
    private InMemoryKeyValueState<String, Map<String, Set<Long>>> transactions;

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    @Override
    public void execute(Tuple tuple) {
        logger.trace("States before: {}", transactions);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        Long transactionId = (Long) tuple.getValueByField(Utils.TRANSACTION_ID);
        String switchId = (String) tuple.getValueByField(FlowTopology.SWITCH_ID_FIELD);
        String flowId = (String) tuple.getValueByField(Utils.FLOW_ID);
        Object message = tuple.getValueByField(FlowTopology.MESSAGE_FIELD);
        Map<String, Set<Long>> flowTransactions;
        Set<Long> flowTransactionIds;
        Values values = null;

        try {
            logger.debug("Request tuple={}", tuple);

            switch (componentId) {

                case TOPOLOGY_ENGINE_BOLT:
                    logger.debug("Transaction from TopologyEngine: switch-id={}, {}={}, {}={}",
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

                    logger.debug("Set status {}: switch-id={}, {}={}, {}={}", FlowState.IN_PROGRESS,
                            switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                    values = new Values(flowId, FlowState.IN_PROGRESS);
                    outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

                    values = new Values(message);
                    outputCollector.emit(streamId.toString(), tuple, values);
                    break;

                case SPEAKER_BOLT:
                    logger.debug("Transaction from Speaker: switch-id={}, {}={}, {}={}",
                            switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                    flowTransactions = transactions.get(switchId);
                    if (flowTransactions != null) {

                        flowTransactionIds = flowTransactions.get(flowId);
                        if (flowTransactionIds != null) {

                            if (flowTransactionIds.remove(transactionId)) {

                                if (flowTransactionIds.isEmpty()) {
                                    logger.debug("Set status {}: switch-id={}, {}={}, {}={}", FlowState.UP,
                                            switchId, Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                                    values = new Values(flowId, FlowState.UP);
                                    outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

                                    flowTransactions.remove(flowId);
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

        } finally {
            logger.debug("Transaction message ack: component={}, stream={}, tuple={}, values={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple, values);

            outputCollector.ack(tuple);
        }

        logger.trace("States after: {}", transactions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, Map<String, Set<Long>>> state) {
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
