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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchFlowCommandsRequest;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.TransactionBoltState;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.rule.FlowCommandErrorData;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.transactions.FlowCommandRegistry;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Transaction Bolt. Tracks OpenFlow Speaker commands transactions.
 */
public class TransactionBolt extends BaseStatefulBolt<InMemoryKeyValueState<String, FlowCommandRegistry>>
        implements ICtrlBolt {
    private static final Logger logger = LoggerFactory.getLogger(TransactionBolt.class);

    private static final String STREAM_ID_CTRL = "ctrl";
    private static final String FLOW_COMMAND_REGISTRY_STATE_KEY = "transactions";

    private final Duration transactionExpirationTime;

    /**
     * FIXME(surabujin) in memory status lead to disaster when system restarts during any transition.
     */
    private transient FlowCommandRegistry flowCommandRegistry;

    private transient TopologyContext context;
    private transient OutputCollector outputCollector;

    public TransactionBolt(Duration transactionExpirationTime) {
        this.transactionExpirationTime = requireNonNull(transactionExpirationTime);
    }

    @Override
    public void execute(Tuple tuple) {
        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }

        logger.debug("Request tuple={}", tuple);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());

        try {
            Message message = (Message) tuple.getValueByField(FlowTopology.MESSAGE_FIELD);
            String flowId = tuple.getStringByField(Utils.FLOW_ID);

            switch (componentId) {
                case CRUD_BOLT:
                    if (message instanceof CommandMessage) {
                        CommandData data = ((CommandMessage) message).getData();
                        if (data instanceof BatchFlowCommandsRequest) {
                            @SuppressWarnings("unchecked")
                            List<List<BaseFlow>> commandGroups = ((BatchFlowCommandsRequest) data).getGroups().stream()
                                    .map(group -> (List<BaseFlow>) group.getFlowCommands())
                                    .collect(toList());
                            logger.info("Transactions from CrudBolt: {}={}, {}",
                                    Utils.FLOW_ID, flowId, commandGroups);

                            flowCommandRegistry.registerBatch(flowId, commandGroups);

                            processCommands(flowId, tuple, message.getCorrelationId());
                        } else {
                            logger.error("Skip undefined command message: {}", message);
                        }
                    } else {
                        logger.error("Skip undefined message: {}", message);
                    }
                    break;

                case SPEAKER_BOLT:
                    if (message instanceof CommandMessage) {
                        CommandData data = ((CommandMessage) message).getData();
                        if (data instanceof BaseFlow) {
                            UUID transactionId = ((BaseFlow) data).getTransactionId();
                            logger.info("Successful transaction from Speaker: {}={}, {}={}",
                                    Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                            flowCommandRegistry.removeCommand(flowId, transactionId);

                            processCommands(flowId, tuple, message.getCorrelationId());
                        } else {
                            logger.error("Skip undefined command message: {}", message);
                        }

                    } else if (message instanceof ErrorMessage) {
                        ErrorData data = ((ErrorMessage) message).getData();
                        if (data instanceof FlowCommandErrorData) {
                            FlowCommandErrorData errorData = (FlowCommandErrorData) data;
                            UUID transactionId = errorData.getTransactionId();
                            logger.error("Failed transaction from Speaker: {}={}, {}={}",
                                    Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId);

                            flowCommandRegistry.removeBatch(flowId, transactionId);

                            Values values = new Values(flowId, FlowState.DOWN,
                                    new CommandContext(message.getCorrelationId()));
                            outputCollector.emit(StreamType.STATUS.toString(), tuple, values);
                        } else {
                            logger.error("Skip undefined error message: {}", message);
                        }

                    } else {
                        logger.error("Skip undefined message: {}", message);
                    }
                    break;

                default:
                    logger.error("Unexpected component: {} in {}", componentId, tuple);
                    break;
            }

            checkForExpiredCommands(tuple, message.getCorrelationId());

        } catch (JsonProcessingException e) {
            logger.error("Could not serialize message", e);
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        } finally {
            outputCollector.ack(tuple);

            logger.debug("Transaction message ack: {}", tuple);
        }
    }

    private void processCommands(String flowId, Tuple tuple, String correlationId) throws JsonProcessingException {
        List<? extends BaseFlow> groupCommands = flowCommandRegistry.pollNextGroup(flowId);
        for (BaseFlow command : groupCommands) {
            CommandMessage message = new CommandMessage(command,
                    System.currentTimeMillis(), correlationId, Destination.CONTROLLER);
            StreamType streamId = command instanceof BaseInstallFlow ? StreamType.CREATE : StreamType.DELETE;
            outputCollector.emit(streamId.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        }

        if (!flowCommandRegistry.hasCommand(flowId)) {
            logger.info("Flow transactions completed for flow: {}", flowId);
            Values values = new Values(flowId, FlowState.UP, new CommandContext(correlationId));
            outputCollector.emit(StreamType.STATUS.toString(), tuple, values);
        }
    }

    private void checkForExpiredCommands(Tuple tuple, String correlationId) {
        Set<String> affectedFlows = flowCommandRegistry.removeExpiredBatch(transactionExpirationTime);
        affectedFlows.forEach(flowId -> {
            Values values = new Values(flowId, FlowState.DOWN, new CommandContext(correlationId));
            outputCollector.emit(StreamType.STATUS.toString(), tuple, values);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, FlowCommandRegistry> state) {
        flowCommandRegistry = state.get(FLOW_COMMAND_REGISTRY_STATE_KEY);
        if (flowCommandRegistry == null) {
            flowCommandRegistry = new FlowCommandRegistry();
            state.put(FLOW_COMMAND_REGISTRY_STATE_KEY, flowCommandRegistry);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), FlowTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), FlowTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), FlowTopology.fieldsFlowIdStatusContext);
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
        Map<String, Set<UUID>> dump = new HashMap<>();
        for (Map.Entry<String, Set<UUID>> item : flowCommandRegistry.getTransactions().entrySet()) {
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
