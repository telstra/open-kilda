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

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.info.flow.FlowOperation.DELETE;
import static org.openkilda.messaging.info.flow.FlowOperation.UPDATE;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCacheSyncRequest;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.SynchronizeCacheAction;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.CrudBoltState;
import org.openkilda.messaging.ctrl.state.FlowDump;
import org.openkilda.messaging.ctrl.state.ResorceCacheBoltState;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowCacheSyncResponse;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.ResourceCache;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.provider.Auth;
import org.openkilda.pce.provider.FlowInfo;
import org.openkilda.pce.provider.PathComputer;
import org.openkilda.pce.provider.PathComputer.Strategy;
import org.openkilda.pce.provider.PathComputerAuth;
import org.openkilda.pce.provider.UnroutablePathException;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.share.utils.FlowCollector;
import org.openkilda.wfm.share.utils.PathComputerFlowFetcher;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class CrudBolt
        extends BaseStatefulBolt<InMemoryKeyValueState<String, FlowCache>>
        implements ICtrlBolt {

    public static final String STREAM_ID_CTRL = "ctrl";

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(CrudBolt.class);

    /**
     * Flow cache key.
     */
    private static final String FLOW_CACHE = "flow";

    /**
     * Path computation instance.
     */
    private PathComputer pathComputer;
    private final PathComputerAuth pathComputerAuth;

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

    private FlowValidator flowValidator;

    /**
     * Instance constructor.
     *
     * @param pathComputerAuth {@link Auth} instance
     */
    public CrudBolt(PathComputerAuth pathComputerAuth) {
        this.pathComputerAuth = pathComputerAuth;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, FlowCache> state) {
        this.caches = state;

        // TODO - do we have to use InMemoryKeyValue, or is there some other InMemory option?
        //  The reason for the qestion .. we are only putting in one object.
        flowCache = state.get(FLOW_CACHE);
        if (flowCache == null) {
            flowCache = new FlowCache();
            this.caches.put(FLOW_CACHE, flowCache);
        }
        initFlowCache();

        flowValidator = new FlowValidator(flowCache);
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
        outputFieldsDeclarer.declareStream(StreamType.CACHE_SYNC.toString(), AbstractTopology.fieldMessage);
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

        pathComputer = pathComputerAuth.getPathComputer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {

        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        String correlationId = Utils.DEFAULT_CORRELATION_ID;

        StreamType streamId = null;
        String flowId = null;
        if (!componentId.equals(ComponentType.LCM_FLOW_SYNC_BOLT)) {
            streamId = StreamType.valueOf(tuple.getSourceStreamId());
            flowId = tuple.getStringByField(Utils.FLOW_ID);
        }

        boolean isRecoverable = false;
        try {
            logger.debug("Request tuple={}", tuple);

            switch (componentId) {
                case SPLITTER_BOLT:
                    Message msg = (Message) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
                    correlationId = msg.getCorrelationId();

                    CommandMessage cmsg = (msg instanceof CommandMessage) ? (CommandMessage) msg : null;
                    InfoMessage imsg = (msg instanceof InfoMessage) ? (InfoMessage) msg : null;

                    logger.info("Flow request: {}={}, {}={}, component={}, stream={}",
                            Utils.CORRELATION_ID, correlationId, Utils.FLOW_ID, flowId, componentId, streamId);

                    switch (streamId) {
                        case CREATE:
                            handleCreateRequest(cmsg, tuple);
                            break;
                        case UPDATE:
                            handleUpdateRequest(cmsg, tuple);
                            break;
                        case DELETE:
                            handleDeleteRequest(flowId, cmsg, tuple);
                            break;
                        case PUSH:
                            handlePushRequest(flowId, imsg, tuple);
                            break;
                        case UNPUSH:
                            handleUnpushRequest(flowId, imsg, tuple);
                            break;
                        case REROUTE:
                            handleRerouteRequest(cmsg, tuple);
                            break;
                        case CACHE_SYNC:
                            handleCacheSyncRequest(cmsg, tuple);
                            break;
                        case READ:
                            handleReadRequest(flowId, cmsg, tuple);
                            break;
                        case DUMP:
                            handleDumpRequest(cmsg, tuple);
                            break;
                        default:

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
                            //TODO: SpeakerBolt & TransactionBolt don't supply a tuple with correlationId
                            handleStateRequest(flowId, newStatus, tuple, correlationId);
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

                case LCM_FLOW_SYNC_BOLT:
                    logger.debug("Got network dump from TE");

                    NetworkInfoData networkDump = (NetworkInfoData) tuple.getValueByField(
                            LcmFlowCacheSyncBolt.FIELD_ID_NETWORK_DUMP);
                    handleFlowSync(networkDump);
                    break;

                default:
                    logger.debug("Unexpected component: {}", componentId);
                    break;
            }
        } catch (RecoverableException e) {
            // FIXME(surabujin): implement retry limit
            logger.error(
                    "Recoverable error (do not try to recoverable it until retry limit will be implemented): {}", e);
            // isRecoverable = true;

        } catch (CacheException exception) {
            String logMessage = format("%s: %s", exception.getErrorMessage(), exception.getErrorDescription());
            logger.error("{}, {}={}, {}={}, component={}, stream={}", logMessage, Utils.CORRELATION_ID,
                    correlationId, Utils.FLOW_ID, flowId, componentId, streamId, exception);

            ErrorMessage errorMessage = buildErrorMessage(correlationId, exception.getErrorType(),
                    logMessage, componentId.toString().toLowerCase());

            Values error = new Values(errorMessage, exception.getErrorType());
            outputCollector.emit(StreamType.ERROR.toString(), tuple, error);

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);

        } finally {
            outputCollector.ack(tuple);

            logger.debug("Command message ack: component={}, stream={}, tuple={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple);

            if (isRecoverable) {
                outputCollector.fail(tuple);
            } else {
                outputCollector.ack(tuple);
            }
        }
    }

    private void handleCacheSyncRequest(CommandMessage message, Tuple tuple) {
        logger.debug("CACHE SYNCE: {}", message);

        // NB: This is going to be a "bulky" operation - get all flows from DB, and synchronize with the cache.

        List<String> droppedFlows = new ArrayList<>();
        List<String> addedFlows = new ArrayList<>();
        List<String> modifiedFlowChanges = new ArrayList<>();
        List<String> modifiedFlowIds = new ArrayList<>();
        List<String> unchangedFlows = new ArrayList<>();

        List<FlowInfo> flowInfos = pathComputer.getFlowInfo();

        // Instead of determining left/right .. store based on flowid_& cookie
        HashMap<String, FlowInfo> flowToInfo = new HashMap<>();
        for (FlowInfo fi : flowInfos) {
            flowToInfo.put(fi.getFlowId() + fi.getCookie(), fi);
        }

        // We first look at comparing what is in the DB to what is in the Cache
        for (FlowInfo fi : flowInfos) {
            String flowid = fi.getFlowId();
            if (flowCache.cacheContainsFlow(flowid)) {
                // TODO: better, more holistic comparison
                // TODO: if the flow is modified, then just leverage drop / add primitives.
                // TODO: Ensure that the DB is always the source of truth - cache and db ops part of transaction.
                // Need to compare both sides
                ImmutablePair<Flow, Flow> fc = flowCache.getFlow(flowid);

                final int count = modifiedFlowChanges.size();
                if (fi.getCookie() != fc.left.getCookie() && fi.getCookie() != fc.right.getCookie()) {
                    modifiedFlowChanges
                            .add("cookie: " + flowid + ":" + fi.getCookie() + ":" + fc.left.getCookie() + ":" + fc.right
                                    .getCookie());
                }
                if (fi.getMeterId() != fc.left.getMeterId() && fi.getMeterId() != fc.right.getMeterId()) {
                    modifiedFlowChanges
                            .add("meter: " + flowid + ":" + fi.getMeterId() + ":" + fc.left.getMeterId() + ":"
                                    + fc.right.getMeterId());
                }
                if (fi.getTransitVlanId() != fc.left.getTransitVlan() && fi.getTransitVlanId() != fc.right
                        .getTransitVlan()) {
                    modifiedFlowChanges
                            .add("transit: " + flowid + ":" + fi.getTransitVlanId() + ":" + fc.left.getTransitVlan()
                                    + ":" + fc.right.getTransitVlan());
                }
                if (!fi.getSrcSwitchId().equals(fc.left.getSourceSwitch()) && !fi.getSrcSwitchId()
                        .equals(fc.right.getSourceSwitch())) {
                    modifiedFlowChanges
                            .add("switch: " + flowid + "|" + fi.getSrcSwitchId() + "|" + fc.left.getSourceSwitch() + "|"
                                    + fc.right.getSourceSwitch());
                }

                if (count == modifiedFlowChanges.size()) {
                    unchangedFlows.add(flowid);
                } else {
                    modifiedFlowIds.add(flowid);
                }
            } else {
                // TODO: need to get the flow from the DB and add it properly
                addedFlows.add(flowid);

            }
        }

        // Now we see if the cache holds things not in the DB
        for (ImmutablePair<Flow, Flow> flow : flowCache.dumpFlows()) {
            String key = flow.left.getFlowId() + flow.left.getCookie();
            // compare the left .. if it is in, then check the right .. o/w remove it (no need to check right
            if (!flowToInfo.containsKey(key)) {
                droppedFlows.add(flow.left.getFlowId());
            } else {
                key = flow.right.getFlowId() + flow.right.getCookie();
                if (!flowToInfo.containsKey(key)) {
                    droppedFlows.add(flow.right.getFlowId());
                }
            }
        }

        FlowCacheSyncRequest request = (FlowCacheSyncRequest) message.getData();
        if (request.getSynchronizeCache() == SynchronizeCacheAction.SYNCHRONIZE_CACHE) {
            synchronizeCache(addedFlows, modifiedFlowIds, droppedFlows, tuple, message.getCorrelationId());
        } else if (request.getSynchronizeCache() == SynchronizeCacheAction.INVALIDATE_CACHE) {
            invalidateCache(addedFlows, modifiedFlowIds, droppedFlows, tuple, message.getCorrelationId());
        }

        FlowCacheSyncResults results = new FlowCacheSyncResults(
                droppedFlows, addedFlows, modifiedFlowChanges, unchangedFlows);
        Values northbound = new Values(new InfoMessage(new FlowCacheSyncResponse(results),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    /**
     * Synchronize the cache, propagate updates further (i.e. emit FlowOperation.CACHE)
     */
    private void synchronizeCache(List<String> addedFlowIds, List<String> modifiedFlowIds, List<String> droppedFlowIds,
            Tuple tuple, String correlationId) {
        logger.info("Synchronizing the flow cache data: {} dropped, {} added, {} modified.",
                droppedFlowIds.size(), addedFlowIds.size(), modifiedFlowIds.size());

        deleteFromCache(droppedFlowIds, tuple, correlationId);

        // override added/modified flows in the cache
        Stream.concat(addedFlowIds.stream(), modifiedFlowIds.stream())
                .map(pathComputer::getFlows)
                .filter(flows -> !flows.isEmpty())
                .map(flows -> {
                    FlowCollector flowPair = new FlowCollector();
                    flows.forEach(flowPair::add);
                    return flowPair;
                })
                .forEach(flowPair -> {
                    final BidirectionalFlow bidirectionalFlow = flowPair.make();
                    final ImmutablePair<Flow, Flow> flow = new ImmutablePair<>(
                            bidirectionalFlow.getForward(), bidirectionalFlow.getReverse());
                    final String flowId = flow.getLeft().getFlowId();
                    logger.debug("Refresh the flow: {}", flowId);

                    flowCache.pushFlow(flow);

                    // propagate updates further
                    emitCacheSyncInfoMessage(flowId, flow, tuple, correlationId);
                });
    }

    /**
     * Purge and re-initialize the cache, propagate updates further (i.e. emit FlowOperation.CACHE)
     */
    private void invalidateCache(List<String> addedFlowIds, List<String> modifiedFlowIds, List<String> droppedFlowIds,
            Tuple tuple, String correlationId) {
        logger.info("Invalidating the flow cache data: {} dropped, {} added, {} modified.",
                droppedFlowIds.size(), addedFlowIds.size(), modifiedFlowIds.size());

        deleteFromCache(droppedFlowIds, tuple, correlationId);

        initFlowCache();

        // propagate updates further
        flowCache.dumpFlows()
                .forEach(flow -> {
                    final String flowId = flow.getLeft().getFlowId();
                    logger.debug("Refresh the flow: {}", flowId);

                    emitCacheSyncInfoMessage(flowId, flow, tuple, correlationId);
                });
    }

    /**
     * Remove the flows from the cache and propagate changes further (i.e. emit FlowOperation.DELETE)
     */
    private void deleteFromCache(List<String> droppedFlowIds, Tuple tuple, String correlationId) {
        droppedFlowIds.forEach(flowId -> {
            logger.debug("Delete the flow: {}", flowId);

            flowCache.removeFlow(flowId);

            emitCacheSyncInfoMessage(flowId, null, tuple, correlationId);
        });
    }

    private void emitCacheSyncInfoMessage(String flowId, @Nullable ImmutablePair<Flow, Flow> flow,
            Tuple tuple, String correlationId) {
        String subCorrelationId = format("%s-%s", correlationId, flowId);
        FlowInfoData data = new FlowInfoData(flowId, flow, FlowOperation.CACHE, subCorrelationId);
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), subCorrelationId);

        try {
            Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
            outputCollector.emit(StreamType.CACHE_SYNC.toString(), tuple, topology);
        } catch (JsonProcessingException e) {
            logger.error("Unable to serialize the message: {}", infoMessage);
        }
    }

    private void handlePushRequest(String flowId, InfoMessage message, Tuple tuple) throws IOException {
        logger.info("PUSH flow: {} :: {}", flowId, message);
        FlowInfoData fid = (FlowInfoData) message.getData();
        ImmutablePair<Flow, Flow> flow = fid.getPayload();

        flowCache.pushFlow(flow);

        // Update Cache
        FlowInfoData data = new FlowInfoData(flow.getLeft().getFlowId(), flow, FlowOperation.PUSH,
                message.getCorrelationId());
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), message.getCorrelationId());
        Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
        outputCollector.emit(StreamType.CREATE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowStatusResponse(
                new FlowIdStatusPayload(flowId, FlowState.UP)), message.getTimestamp(),
                message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleUnpushRequest(String flowId, InfoMessage message, Tuple tuple) throws IOException {
        logger.info("UNPUSH flow: {} :: {}", flowId, message);

        ImmutablePair<Flow, Flow> flow = flowCache.deleteFlow(flowId);

        // Update Cache
        FlowInfoData data = new FlowInfoData(flowId, flow, FlowOperation.UNPUSH, message.getCorrelationId());
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), message.getCorrelationId());
        Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
        outputCollector.emit(StreamType.DELETE.toString(), tuple, topology);


        Values northbound = new Values(new InfoMessage(new FlowStatusResponse(
                new FlowIdStatusPayload(flowId, FlowState.DOWN)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }


    private void handleDeleteRequest(String flowId, CommandMessage message, Tuple tuple) throws IOException {
        ImmutablePair<Flow, Flow> flow = flowCache.deleteFlow(flowId);

        logger.info("Deleted flow: {}", flowId);

        FlowInfoData data = new FlowInfoData(flowId, flow, DELETE, message.getCorrelationId());
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), message.getCorrelationId());
        Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
        outputCollector.emit(StreamType.DELETE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleCreateRequest(CommandMessage message, Tuple tuple) throws IOException, RecoverableException {
        Flow requestedFlow = ((FlowCreateRequest) message.getData()).getPayload();

        ImmutablePair<PathInfoData, PathInfoData> path;
        try {
            flowValidator.validate(requestedFlow);

            path = pathComputer.getPath(requestedFlow, Strategy.COST);
            logger.info("Creating flow {}. Found path: {}, correlationId: {}", requestedFlow.getFlowId(), path,
                    message.getCorrelationId());

        } catch (FlowValidationException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.ALREADY_EXISTS, "Could not create flow", e.getMessage());
        } catch (UnroutablePathException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, "Could not create flow",
                    "Not enough bandwidth found or path not found");
        }

        ImmutablePair<Flow, Flow> flow = flowCache.createFlow(requestedFlow, path);
        logger.info("Created flow: {}, correlationId: {}", flow, message.getCorrelationId());

        FlowInfoData data = new FlowInfoData(requestedFlow.getFlowId(), flow, FlowOperation.CREATE,
                message.getCorrelationId());
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), message.getCorrelationId());
        Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
        outputCollector.emit(StreamType.CREATE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleRerouteRequest(CommandMessage message, Tuple tuple) throws IOException, RecoverableException {
        FlowRerouteRequest request = (FlowRerouteRequest) message.getData();
        final String flowId = request.getFlowId();
        final String correlationId = message.getCorrelationId();
        logger.warn("Handling reroute request with correlationId {}", correlationId);

        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);
        switch (request.getOperation()) {
            case UPDATE:
                final Flow flowForward = flow.getLeft();

                try {
                    logger.warn("Origin flow {} path: {} correlationId {}", flowId, flowForward.getFlowPath(),
                            correlationId);
                    AvailableNetwork network = pathComputer.getAvailableNetwork(flowForward.isIgnoreBandwidth(),
                            flowForward.getBandwidth());
                    network.addIslsOccupiedByFlow(flowId, flowForward.isIgnoreBandwidth(), flowForward.getBandwidth());
                    ImmutablePair<PathInfoData, PathInfoData> path =
                            pathComputer.getPath(flow.getLeft(), network, Strategy.COST);
                    logger.warn("Potential New Path for flow {} with LEFT path: {}, RIGHT path: {} correlationId {}",
                            flowId, path.getLeft(), path.getRight(), correlationId);
                    boolean isFoundNewPath = (
                            !path.getLeft().equals(flow.getLeft().getFlowPath())
                                       || !path.getRight().equals(flow.getRight().getFlowPath())
                                       || !isFlowActive(flow));
                    //no need to emit changes if path wasn't changed and flow is active.
                    //force means to update flow even if path is not changed.
                    if (isFoundNewPath || request.isForce()) {
                        flow.getLeft().setState(FlowState.DOWN);
                        flow.getRight().setState(FlowState.DOWN);

                        flow = flowCache.updateFlow(flow.getLeft(), path);
                        logger.warn("Rerouted flow with new path: {}, correlationId {}", flow, correlationId);

                        FlowInfoData data = new FlowInfoData(flowId, flow, UPDATE, correlationId);
                        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), correlationId);
                        Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
                        outputCollector.emit(StreamType.UPDATE.toString(), tuple, topology);
                    } else {
                        logger.warn("Reroute {} is unsuccessful: can't find new path. CorrelationId: {}",
                                flowId, correlationId);
                    }

                    logger.debug("Sending response to NB. Correlation id {}", correlationId);
                    FlowRerouteResponse response = new FlowRerouteResponse(flow.left.getFlowPath(), isFoundNewPath);
                    Values values = new Values(new InfoMessage(response, message.getTimestamp(),
                            correlationId, Destination.NORTHBOUND));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
                } catch (UnroutablePathException e) {
                    logger.warn("There is no path available for the flow {}, correlationId: {}", flowId,
                            correlationId);
                    flow.getLeft().setState(FlowState.DOWN);
                    flow.getRight().setState(FlowState.DOWN);
                    throw new MessageException(correlationId, System.currentTimeMillis(),
                            ErrorType.UPDATE_FAILURE, "Could not reroute flow", "Path was not found");
                }
                break;

            case CREATE:
                logger.warn("State flow: {}={}, correlationId: {}", flowId, FlowState.UP, correlationId);
                flow.getLeft().setState(FlowState.UP);
                flow.getRight().setState(FlowState.UP);
                break;

            case DELETE:
                logger.warn("State flow: {}={}, correlationId: {}", flowId, FlowState.DOWN, correlationId);
                flow.getLeft().setState(FlowState.DOWN);
                flow.getRight().setState(FlowState.DOWN);
                break;

            default:
                logger.warn("Flow {} undefined reroute operation", request.getOperation());
                break;
        }
    }

    private void handleUpdateRequest(CommandMessage message, Tuple tuple) throws IOException, RecoverableException {
        Flow requestedFlow = ((FlowUpdateRequest) message.getData()).getPayload();
        String correlationId = message.getCorrelationId();

        ImmutablePair<PathInfoData, PathInfoData> path;
        try {
            flowValidator.validate(requestedFlow);

            AvailableNetwork network = pathComputer.getAvailableNetwork(requestedFlow.isIgnoreBandwidth(),
                    requestedFlow.getBandwidth());
            network.addIslsOccupiedByFlow(requestedFlow.getFlowId(),
                    requestedFlow.isIgnoreBandwidth(), requestedFlow.getBandwidth());
            path = pathComputer.getPath(requestedFlow, network, Strategy.COST);
            logger.info("Updated flow path: {}, correlationId {}", path, correlationId);

        } catch (FlowValidationException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.ALREADY_EXISTS, "Could not update flow", e.getMessage());
        } catch (UnroutablePathException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, "Could not update flow", "Path was not found");
        }

        ImmutablePair<Flow, Flow> flow = flowCache.updateFlow(requestedFlow, path);
        logger.info("Updated flow: {}, correlationId {}", flow, correlationId);

        FlowInfoData data = new FlowInfoData(requestedFlow.getFlowId(), flow, UPDATE,
                message.getCorrelationId());
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), message.getCorrelationId());
        Values topology = new Values(MAPPER.writeValueAsString(infoMessage));
        outputCollector.emit(StreamType.UPDATE.toString(), tuple, topology);

        Values northbound = new Values(new InfoMessage(new FlowResponse(buildFlowResponse(flow)),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    private void handleDumpRequest(CommandMessage message, Tuple tuple) {
        List<BidirectionalFlow> flows = flowCache.dumpFlows().stream()
                .map(BidirectionalFlow::new)
                .collect(Collectors.toList());

        logger.debug("Dump flows: found {} items", flows.size());

        String requestId = message.getCorrelationId();
        if (flows.isEmpty()) {
            Message response = new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId, null);
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, new Values(response));
        } else {
            Iterator<BidirectionalFlow> iterator = flows.iterator();
            do {
                BidirectionalFlow flow = iterator.next();
                String nextRequestId = iterator.hasNext() ? UUID.randomUUID().toString() : null;

                Message response = new ChunkedInfoMessage(
                        new FlowReadResponse(flow), System.currentTimeMillis(), requestId, nextRequestId);
                outputCollector.emit(StreamType.RESPONSE.toString(), tuple, new Values(response));
                requestId = nextRequestId;
            } while (iterator.hasNext());
        }
    }

    private void handleReadRequest(String flowId, CommandMessage message, Tuple tuple) {
        BidirectionalFlow flow = new BidirectionalFlow(flowCache.getFlow(flowId));

        logger.debug("Got bidirectional flow: {}, correlationId {}", flow, message.getCorrelationId());

        Values northbound = new Values(
                new InfoMessage(
                        new FlowReadResponse(flow),
                        message.getTimestamp(),
                        message.getCorrelationId(),
                        Destination.NORTHBOUND));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, northbound);
    }

    /**
     * This method changes the state of the Flow. It sets the state of both left and right to the
     * same state.
     * It is currently called from 2 places - a failed update (set flow to DOWN), and a STATUS
     * update from the TransactionBolt.
     */
    private void handleStateRequest(String flowId, FlowState state, Tuple tuple, String correlationId)
            throws IOException {
        ImmutablePair<Flow, Flow> flow = flowCache.getFlow(flowId);
        logger.info("State flow: {}={}", flowId, state);
        flow.getLeft().setState(state);
        flow.getRight().setState(state);

        FlowInfoData data = new FlowInfoData(flowId, flow, FlowOperation.STATE, correlationId);
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), correlationId);

        Values topology = new Values(Utils.MAPPER.writeValueAsString(infoMessage));
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
                handleStateRequest(flowId, FlowState.DOWN, tuple, message.getCorrelationId());
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

    private void handleFlowSync(NetworkInfoData networkDump) {
        Set<ImmutablePair<Flow, Flow>> flows = networkDump.getFlows();

        logger.info("Load flows {}", flows.size());
        flows.forEach(flowCache::putFlow);
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

    private boolean isFlowActive(ImmutablePair<Flow, Flow> flowPair) {
        return flowPair.getLeft().getState().isActive() && flowPair.getRight().getState().isActive();
    }

    private void initFlowCache() {
        PathComputerFlowFetcher flowFetcher = new PathComputerFlowFetcher(pathComputer);

        for (BidirectionalFlow bidirectionalFlow : flowFetcher.getFlows()) {
            ImmutablePair<Flow, Flow> flowPair = new ImmutablePair<>(
                    bidirectionalFlow.getForward(), bidirectionalFlow.getReverse());
            flowCache.pushFlow(flowPair);
        }
    }

    @Override
    public AbstractDumpState dumpState() {
        FlowDump flowDump = new FlowDump(flowCache.dumpFlows());
        return new CrudBoltState(flowDump);
    }

    @VisibleForTesting
    @Override
    public void clearState() {
        logger.info("State clear request from test");
        initState(new InMemoryKeyValueState<>());
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(SwitchId switchId) {
        // Not implemented
        return new CrudBoltState(new FlowDump(new HashSet<>()));
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

    @Override
    public Optional<AbstractDumpState> dumpResorceCacheState() {
        return Optional.of(new ResorceCacheBoltState(
                flowCache.getAllocatedMeters(),
                flowCache.getAllocatedVlans(),
                flowCache.getAllocatedCookies()));
    }
}
