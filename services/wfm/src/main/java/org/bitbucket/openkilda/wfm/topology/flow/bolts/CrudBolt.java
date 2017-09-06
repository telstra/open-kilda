package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.fieldMessage;
import static org.bitbucket.openkilda.wfm.topology.cache.StateBolt.FLOW_CACHE;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.FLOW_ID_FIELD;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageErrorType;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.pce.cache.FlowCache;
import org.bitbucket.openkilda.pce.provider.PathComputer;
import org.bitbucket.openkilda.wfm.topology.flow.ComponentType;
import org.bitbucket.openkilda.wfm.topology.flow.StreamType;

import org.apache.commons.lang3.tuple.ImmutablePair;
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

        Values values = new Values(MAPPER.writeValueAsString(message));
        outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

        return new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
    }

    private InfoMessage handleCreateRequest(CommandMessage message, Tuple tuple) throws IOException {

        FlowCreateRequest creation = (FlowCreateRequest) message.getData();
        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(creation.getPayload());
        ImmutablePair<Flow, Flow> flow = flowCache.createFlow(creation.getPayload(), path);

        logger.debug("Created flow path: {}", path);
        logger.debug("Created flow: {}", flow);

        CommandMessage command = new CommandMessage(new FlowCreateRequest(flow.left),
                message.getTimestamp(), message.getCorrelationId(), Destination.TOPOLOGY_ENGINE);

        Values values = new Values(Utils.MAPPER.writeValueAsString(command));
        outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

        command = new CommandMessage(new FlowCreateRequest(flow.right),
                message.getTimestamp(), message.getCorrelationId(), Destination.TOPOLOGY_ENGINE);

        values = new Values(Utils.MAPPER.writeValueAsString(command));
        outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

        return new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND);
    }

    private InfoMessage handleUpdateRequest(CommandMessage message, Tuple tuple) throws IOException {

        FlowUpdateRequest update = (FlowUpdateRequest) message.getData();
        ImmutablePair<PathInfoData, PathInfoData> path = pathComputer.getPath(update.getPayload());
        ImmutablePair<Flow, Flow> flow = flowCache.updateFlow(update.getPayload(), path);

        logger.debug("Updated flow path: {}", path);
        logger.debug("Updated flow: {}", flow);

        CommandMessage command = new CommandMessage(new FlowUpdateRequest(flow.left),
                message.getTimestamp(), message.getCorrelationId(), Destination.TOPOLOGY_ENGINE);

        Values values = new Values(Utils.MAPPER.writeValueAsString(command));
        outputCollector.emit(StreamType.UPDATE.toString(), tuple, values);

        command = new CommandMessage(new FlowUpdateRequest(flow.right),
                message.getTimestamp(), message.getCorrelationId(), Destination.TOPOLOGY_ENGINE);

        values = new Values(Utils.MAPPER.writeValueAsString(command));
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
}
