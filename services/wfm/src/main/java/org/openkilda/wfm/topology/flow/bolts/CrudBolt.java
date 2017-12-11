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

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowRestoreRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.CrudBoltState;
import org.openkilda.messaging.ctrl.state.FlowDump;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.ResourceCache;
import org.openkilda.pce.provider.PathComputer;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CrudBolt
        extends BaseStatefulBolt<InMemoryKeyValueState<String, FlowCache>>
        implements ICtrlBolt {

    public static final String STREAM_ID_CTRL = "ctrl";

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(CrudBolt.class);

    /**
     * Flow cache key.
     */
    private static final String FLOW_CACHE = "flow";

    /**
     * Path computation instance.
     */
    private PathComputer pathComputer;

    /**
     * Flows state.
     */
    private InMemoryKeyValueState<String, FlowCache> caches;

    private TopologyContext context;
    private OutputCollector outputCollector;

    /**
     * Flow cache.
     */
    private FlowCache flowCache;

    /**
     * Instance constructor.
     *
     * @param pathComputer {@link PathComputer} instance
     */
    public CrudBolt(PathComputer pathComputer) {
        this.pathComputer = pathComputer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, FlowCache> state) {
        this.caches = state;

        flowCache = state.get(FLOW_CACHE);
        if (flowCache == null) {
            flowCache = new FlowCache();
            this.caches.put(FLOW_CACHE, flowCache);
        }

        pathComputer.init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.UPDATE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.ERROR.toString(), FlowTopology.fieldsMessageErrorType);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {

        logger.warn("FRED 1");
        if (CtrlAction.boltHandlerEntrance(this, tuple))
            return;

        logger.warn("FRED 2");
        logger.trace("Flow Cache before: {}", flowCache);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        String flowId = tuple.getStringByField(Utils.FLOW_ID);
        String correlationId = Utils.DEFAULT_CORRELATION_ID;

        logger.warn("FRED 3");
        try {
            logger.debug("Request tuple={}", tuple);

            logger.warn("FRED 4");
            switch (componentId) {

                case SPLITTER_BOLT:
                    CommandMessage message = (CommandMessage) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
                    correlationId = message.getCorrelationId();

                    logger.info("Flow request: {}={}, {}={}, component={}, stream={}",
                            Utils.CORRELATION_ID, correlationId, Utils.FLOW_ID, flowId, componentId, streamId);

                    switch (streamId) {
                        case CREATE:
                            handleCreateRequest(message, tuple);
                            break;
                        case UPDATE:
                            handleUpdateRequest(message, tuple);
                            break;
                        case DELETE:
                            handleDeleteRequest(flowId, message, tuple);
                            break;
                        case PATH:
                            handlePathRequest(flowId, message, tuple);
                            break;
                        case RESTORE:
                            handleRestoreRequest(message, tuple);
                            break;
                        case REROUTE:
                            handleRerouteRequest(message, tuple);
                            break;
                        case STATUS:
                            logger.warn("FRED 5");
                            handleStatusRequest(flowId, message, tuple);
                            break;
                        case READ:
                            if (flowId != null) {
                                handleReadRequest(flowId, message, tuple);
                            } else {
                                handleDumpRequest(message, tuple);
                            }
                            break;
                        default:
                            logger.warn("FRED 6");

                            logger.debug("Unexpected stream: component={}, stream={}", componentId, streamId);
                            break;
                    }
                    break;

                case SPEAKER_BOLT:
                case TRANSACTION_BOLT:
                    FlowState newStatus = (FlowState) tuple.getValueByField(FlowTopology.STATUS_FIELD);

                    logger.info("Flow {} status {}: component={}, stream={}", flowId, newStatus, componentId, streamId);

                    switch (streamId) {
                        case STATUS:
                            handleStateRequest(flowId, newStatus, tuple);
                            break;
                        default:
                            logger.debug("Unexpected stream: component={}, stream={}", componentId, streamId);
                            break;
                    }
                    break;

                case TOPOLOGY_ENGINE_BOLT:
                    ErrorMessage errorMessage = (ErrorMessage) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);

                    logger.info("Flow {} error: component={}, stream={}", flowId, componentId, streamId);

                    switch (streamId) {
                        case STATUS:
                            handleErrorRequest(flowId, errorMessage, tuple);
                            break;
                        default:
                            logger.debug("Unexpected stream: component={}, stream={}", componentId, streamId);
                            break;
                    }
                    break;

                default:
                    logger.debug("Unexpected component: {}", componentId);
                    break;
            }
        } catch (CacheException exception) {
            String logMessage = String.format("%s: %s", exception.getErrorMessage(), exception.getErrorDescription());
            logger.error("{}, {}={}, {}={}, component={}, stream={}", logMessage, Utils.CORRELATION_ID,
                    correlationId, Utils.FLOW_ID, flowId, componentId, streamId, exception);

            ErrorMessage errorMessage = buildErrorMessage(correlationId, exception.getErrorType(),
                    logMessage, componentId.toString().toLowerCase());

            Values error = new Values(errorMessage, exception.getErrorType());
            outputCollector.emit(StreamType.ERROR.toString(), tuple, error);

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } finally {
            logger.debug("Command message ack: component={}, stream={}, tuple={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple);

            outputCollector.ack(tuple);
        }

        logger.trace("Flow Cache after: {}", flowCache);
    }

    private void handleDeleteRequest(String flowId, CommandMessage message, Tuple tuple) throws IOException {
        ImmutablePair<Flow, Flow> flow = flowCache.deleteFlow(flowId);

        logger.info("Deleted flow: {}", flow);

        Values topology = new Values(MAPPER.writeValueAsString(
                new FlowInfoData(flowId, flow, FlowOperation.DELETE, message.getCorrelationId())));
        outputCollector.emit(StreamType.DELETE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleCreateRequest(CommandMessage message, Tuple tuple) throws IOException {
        Flow requestedFlow = ((FlowCreateRequest) message.getData()).getPayload();

        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(requestedFlow);
        logger.info("Created flow path: {}", path);

        if (!flowCache.isOneSwitchFlow(requestedFlow) && pathComputer.isEmpty(path)) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.CREATION_FAILURE, "Could not create flow", "Path was not found");
        }

        ImmutablePair<Flow, Flow> flow = flowCache.createFlow(requestedFlow, path);
        logger.info("Created flow: {}", flow);

        Values topology = new Values(Utils.MAPPER.writeValueAsString(
                new FlowInfoData(requestedFlow.getFlowId(), flow, FlowOperation.CREATE, message.getCorrelationId())));
        outputCollector.emit(StreamType.CREATE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleRerouteRequest(CommandMessage message, Tuple tuple) throws IOException {
        FlowRerouteRequest request = (FlowRerouteRequest) message.getData();
        Flow requestedFlow = request.getPayload();
        ImmutablePair<Flow, Flow> flow;

        switch (request.getOperation()) {

            case UPDATE:
                flow = flowCache.getFlow(requestedFlow.getFlowId());
                flow.getLeft().setState(FlowState.DOWN);
                flow.getRight().setState(FlowState.DOWN);

                ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(requestedFlow);
                logger.info("Rerouted flow path: {}", path);

                if (!flowCache.isOneSwitchFlow(requestedFlow) && pathComputer.isEmpty(path)) {
                    throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                            ErrorType.UPDATE_FAILURE, "Could not create flow", "Path was not found");
                }

                flow = flowCache.updateFlow(requestedFlow, path);
                logger.info("Rerouted flow: {}", flow);

                Values topology = new Values(Utils.MAPPER.writeValueAsString(
                        new FlowInfoData(requestedFlow.getFlowId(), flow,
                                FlowOperation.UPDATE, message.getCorrelationId())));
                outputCollector.emit(StreamType.UPDATE.toString(), tuple, topology);

                Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                        message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
                outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
                break;

            case CREATE:
                flow = flowCache.getFlow(requestedFlow.getFlowId());
                logger.info("State flow: {}={}", flow.getLeft().getFlowId(), FlowState.UP);
                flow.getLeft().setState(FlowState.UP);
                flow.getRight().setState(FlowState.UP);
                break;

            case DELETE:
                flow = flowCache.getFlow(requestedFlow.getFlowId());
                logger.info("State flow: {}={}", flow.getLeft().getFlowId(), FlowState.DOWN);
                flow.getLeft().setState(FlowState.DOWN);
                flow.getRight().setState(FlowState.DOWN);
                break;

            default:
                logger.warn("Flow {} undefined reroute operation", request.getOperation());
                break;
        }
    }

    private void handleRestoreRequest(CommandMessage message, Tuple tuple) throws IOException {
        ImmutablePair<Flow, Flow> requestedFlow = ((FlowRestoreRequest) message.getData()).getPayload();

        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(requestedFlow.getLeft());
        logger.info("Restored flow path: {}", path);

        if (!flowCache.isOneSwitchFlow(requestedFlow) && pathComputer.isEmpty(path)) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.CREATION_FAILURE, "Could not restore flow", "Path was not found");
        }

        ImmutablePair<Flow, Flow> flow;
        if (flowCache.cacheContainsFlow(requestedFlow.getLeft().getFlowId())) {
            flow = flowCache.updateFlow(requestedFlow, path);
        } else {
            flow = flowCache.createFlow(requestedFlow, path);
        }
        logger.info("Restored flow: {}", flow);

        Values topology = new Values(Utils.MAPPER.writeValueAsString(
                new FlowInfoData(requestedFlow.getLeft().getFlowId(), flow,
                        FlowOperation.UPDATE, message.getCorrelationId())));
        outputCollector.emit(StreamType.UPDATE.toString(), tuple, topology);
    }

    private void handleUpdateRequest(CommandMessage message, Tuple tuple) throws IOException {
        Flow requestedFlow = ((FlowUpdateRequest) message.getData()).getPayload();

        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(requestedFlow);
        logger.info("Updated flow path: {}", path);

        if (!flowCache.isOneSwitchFlow(requestedFlow) && pathComputer.isEmpty(path)) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.UPDATE_FAILURE, "Could not create flow", "Path was not found");
        }

        ImmutablePair<Flow, Flow> flow = flowCache.updateFlow(requestedFlow, path);
        logger.info("Updated flow: {}", flow);

        Values topology = new Values(Utils.MAPPER.writeValueAsString(
                new FlowInfoData(requestedFlow.getFlowId(), flow, FlowOperation.UPDATE, message.getCorrelationId())));
        outputCollector.emit(StreamType.UPDATE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleDumpRequest(CommandMessage message, Tuple tuple) {
        List<Flow> flows = flowCache.dumpFlows().stream().map(this::buildFlowResponse).collect(Collectors.toList());

        logger.info("Dump flows: {}", flows);

        Values northbound = new Values(new InfoMessage(new FlowsResponse(flows),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleReadRequest(String flowId, CommandMessage message, Tuple tuple) {
        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);

        logger.info("Got flow: {}", flow);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handlePathRequest(String flowId, CommandMessage message, Tuple tuple) throws IOException {
        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);

        logger.info("Path flow: {}", flow);

        Values northbound = new Values(new InfoMessage(new FlowPathResponse(flow.left.getFlowPath()),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleStatusRequest(String flowId, CommandMessage message, Tuple tuple) throws IOException {
        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);
        FlowState status = flow.getLeft().getState();

        logger.info("Status flow: {}={}", flowId, status);

        Values northbound = new Values(new InfoMessage(new FlowStatusResponse(new FlowIdStatusPayload(flowId, status)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleStateRequest(String flowId, FlowState state, Tuple tuple) throws IOException {
        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);
        logger.info("State flow: {}={}", flowId, state);
        flow.getLeft().setState(state);
        flow.getRight().setState(state);

        Values topology = new Values(Utils.MAPPER.writeValueAsString(
                new FlowInfoData(flowId, flow, FlowOperation.STATE, Utils.SYSTEM_CORRELATION_ID)));
        outputCollector.emit(StreamType.STATUS.toString(), tuple, topology);

    }

    private void handleErrorRequest(String flowId, ErrorMessage message, Tuple tuple) throws IOException {
        ErrorType errorType = message.getData().getErrorType();
        message.getData().setErrorDescription("topology-engine internal error");

        logger.info("Flow {} {} failure", errorType, flowId);

        switch (errorType) {
            case CREATION_FAILURE:
                flowCache.removeFlow(flowId);
                break;

            case UPDATE_FAILURE:
                handleStateRequest(flowId, FlowState.DOWN, tuple);
                break;

            case DELETION_FAILURE:
                break;

            case INTERNAL_ERROR:
                break;

            default:
                logger.warn("Flow {} undefined failure", flowId);

        }

        Values error = new Values(message, errorType);
        outputCollector.emit(StreamType.ERROR.toString(), tuple, error);
    }

    /**
     * Builds response flow.
     *
     * @param flow cache flow
     * @return response flow model
     */
    private Flow buildFlowResponse(ImmutablePair<Flow, Flow> flow) {
        Flow response = new Flow(flow.left);
        response.setCookie(response.getCookie() & ResourceCache.FLOW_COOKIE_VALUE_MASK);
        return response;
    }

    private ErrorMessage buildErrorMessage(String correlationId, ErrorType type, String message, String description) {
        return new ErrorMessage(new ErrorData(type, message, description),
                System.currentTimeMillis(), correlationId, Destination.NORTHBOUND);
    }

    @Override
    public AbstractDumpState dumpState() {
        FlowDump flowDump = new FlowDump(flowCache.dumpFlows());
        return new CrudBoltState(flowDump);
    }

    @Override
    public String getCtrlStreamId() {
        return STREAM_ID_CTRL;
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
