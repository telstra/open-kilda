package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.fieldMessage;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.FLOW_ID_FIELD;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageErrorType;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.error.CacheException;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowOperation;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;
import org.bitbucket.openkilda.pce.cache.FlowCache;
import org.bitbucket.openkilda.pce.provider.PathComputer;
import org.bitbucket.openkilda.wfm.topology.flow.ComponentType;
import org.bitbucket.openkilda.wfm.topology.flow.StreamType;

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

public class CrudBolt extends BaseStatefulBolt<InMemoryKeyValueState<String, FlowCache>> {
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

    /**
     * Output collector.
     */
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
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.UPDATE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.ERROR.toString(), fieldsMessageErrorType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        logger.trace("Flow Cache before: {}", flowCache);

        logger.debug("Ingoing tuple: {}", tuple);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamType = StreamType.valueOf(tuple.getSourceStreamId());
        String flowId = (String) tuple.getValueByField(FLOW_ID_FIELD);
        CommandMessage message = (CommandMessage) tuple.getValueByField(MESSAGE_FIELD);

        try {
            if (ComponentType.STATUS_BOLT == componentId) {

                switch (streamType) {
                    case CREATE:
                        emitFlowResponse(handleCreateRequest(message, tuple), tuple);
                        break;
                    case UPDATE:
                        emitFlowResponse(handleUpdateRequest(message, tuple), tuple);
                        break;
                    case DELETE:
                        emitFlowResponse(handleDeleteRequest(flowId, message, tuple), tuple);
                        break;
                    case READ:
                        emitFlowResponse(handleReadRequest(flowId, message), tuple);
                        break;
                    case PATH:
                        emitFlowResponse(handlePathRequest(flowId, message), tuple);
                        break;
                    default:
                        logger.warn("Unexpected stream: {}", streamType);
                        break;
                }
            } else {
                logger.warn("Unexpected component: {}", componentId);
            }
        } catch (CacheException exception) {
            String logMessage = String.format("%s: %s", exception.getErrorMessage(), exception.getErrorDescription());
            logger.error("{}, {}={}, {}={}, component={}, stream={}", logMessage, Utils.CORRELATION_ID,
                    message.getCorrelationId(), Utils.FLOW_ID, flowId, componentId, streamType, exception);

            ErrorMessage errorMessage = buildErrorMessage(message.getCorrelationId(),
                    exception.getErrorType(), logMessage, componentId.toString().toLowerCase());

            Values values = new Values(errorMessage, exception.getErrorType());
            outputCollector.emit(StreamType.ERROR.toString(), tuple, values);

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } finally {
            logger.debug("Command ack: component={}, stream={}", componentId, streamType);

            outputCollector.ack(tuple);
        }

        logger.trace("Flow Cache after: {}", flowCache);
    }

    private InfoMessage handleDeleteRequest(String flowId, CommandMessage message, Tuple tuple) throws IOException {

        ImmutablePair<Flow, Flow> flow = flowCache.deleteFlow(flowId);

        logger.debug("Deleted flow: {}", flow);

        Values values = new Values(MAPPER.writeValueAsString(new FlowInfoData(flow, FlowOperation.DELETE)));
        outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

        return new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
    }

    private InfoMessage handleCreateRequest(CommandMessage message, Tuple tuple) throws IOException {

        FlowCreateRequest creation = (FlowCreateRequest) message.getData();

        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(creation.getPayload());
        if (!creation.getPayload().getSourceSwitch().equals(creation.getPayload().getDestinationSwitch())
                && (path.getLeft().getPath().isEmpty() || path.getRight().getPath().isEmpty())) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.CREATION_FAILURE, "Could not create flow", "Path was not found");
        }

        ImmutablePair<Flow, Flow> flow = flowCache.createFlow(creation.getPayload(), path);

        logger.debug("Created flow path: {}", path);
        logger.debug("Created flow: {}", flow);

        Values values = new Values(Utils.MAPPER.writeValueAsString(new FlowInfoData(flow, FlowOperation.CREATE)));
        outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

        return new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
    }

    private InfoMessage handleUpdateRequest(CommandMessage message, Tuple tuple) throws IOException {

        FlowUpdateRequest update = (FlowUpdateRequest) message.getData();

        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(update.getPayload());
        if (!update.getPayload().getSourceSwitch().equals(update.getPayload().getDestinationSwitch())
                && (path.getLeft().getPath().isEmpty() || path.getRight().getPath().isEmpty())) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.UPDATE_FAILURE, "Could not create flow", "Path was not found");
        }

        ImmutablePair<Flow, Flow> flow = flowCache.updateFlow(update.getPayload(), path);

        logger.debug("Updated flow path: {}", path);
        logger.debug("Updated flow: {}", flow);

        Values values = new Values(Utils.MAPPER.writeValueAsString(new FlowInfoData(flow, FlowOperation.UPDATE)));
        outputCollector.emit(StreamType.UPDATE.toString(), tuple, values);

        return new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
    }

    private InfoMessage handleReadRequest(String flowId, CommandMessage message) {
        InfoMessage response;

        if (flowId != null) {

            ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);

            logger.debug("Red flow: {}", flow);

            response = new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);

        } else {

            logger.debug("Dump flows");

            List<Flow> flows = flowCache.dumpFlows().stream()
                    .map(this::buildFlowResponse)
                    .collect(Collectors.toList());

            response = new InfoMessage(new FlowsResponse(flows),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
        }

        return response;
    }

    private InfoMessage handlePathRequest(String flowId, CommandMessage message) throws IOException {

        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);

        logger.debug("Path flow: {}", flow);

        return new InfoMessage(new FlowPathResponse(flow.left.getFlowPath()),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
    }

    private void emitFlowResponse(InfoMessage response, Tuple tuple) {
        Values values = new Values(response);
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
    }

    /**
     * Builds response flow.
     *
     * @param flow cache flow
     * @return response flow model
     */
    private Flow buildFlowResponse(ImmutablePair<Flow, Flow> flow) {
        Flow response = new Flow(flow.left);
        response.setCookie(response.getCookie() & FlowCache.FLOW_COOKIE_VALUE_MASK);
        return response;
    }

    private ErrorMessage buildErrorMessage(String correlationId, ErrorType type, String message, String description) {
        return new ErrorMessage(new ErrorData(type, message, description),
                System.currentTimeMillis(), correlationId, Destination.NORTHBOUND);
    }
}
