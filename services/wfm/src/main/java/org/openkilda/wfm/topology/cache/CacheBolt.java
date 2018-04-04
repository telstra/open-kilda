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

package org.openkilda.wfm.topology.cache;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowRestoreRequest;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.CacheBoltState;
import org.openkilda.messaging.ctrl.state.FlowDump;
import org.openkilda.messaging.ctrl.state.NetworkDump;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.ChunkDescriptor;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.NetworkTopologyChange;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.cache.Cache;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.NetworkCache;
import org.openkilda.pce.cache.ResourceCache;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.cache.service.CacheWarmingService;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CacheBolt
        extends AbstractTickStatefulBolt<InMemoryKeyValueState<String, Cache>>
        implements ICtrlBolt {
    public static final String STREAM_ID_CTRL = "ctrl";

    /**
     * Network cache key.
     */
    private static final String NETWORK_CACHE = "network";

    /**
     * Network cache key.
     */
    private static final String FLOW_CACHE = "flow";

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(CacheBolt.class);

    /**
     * Network cache.
     */
    private NetworkCache networkCache;

    /**
     * Flow cache.
     */
    private FlowCache flowCache;

    /**
     * Network cache cache.
     */
    private InMemoryKeyValueState<String, Cache> state;

    /**
     * We need to store rerouted flows for ability to restore initial path if it is possible.
     * Here is a mapping between switch and all flows that was rerouted because switch went down.
     */
    private final Map<String, Set<String>> reroutedFlows = new ConcurrentHashMap<>();

    private CacheWarmingService cacheWarmingService;

    private String dumpRequestCorrelationId = null;
    private Set<Integer> dumpRequestUnprocessedChunks = null;

    private TopologyContext context;
    private OutputCollector outputCollector;

    /**
     * Time passed.
     */
    private int timePassed = 0;

    /**
     * Discovery interval.
     */
    private final int discoveryInterval;

    /**
     * Instance constructor.
     *
     * @param discoveryInterval discovery interval
     */
    CacheBolt(int discoveryInterval) {
        this.discoveryInterval = discoveryInterval;
    }

    /**
     * Initialization flag
     */
    private boolean isReceivedCacheInfo = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, Cache> state) {
        this.state = state;

        networkCache = (NetworkCache) state.get(NETWORK_CACHE);
        if (networkCache == null) {
            networkCache = new NetworkCache();
            this.state.put(NETWORK_CACHE, networkCache);
        }

        flowCache = (FlowCache) state.get(FLOW_CACHE);
        if (flowCache == null) {
            flowCache = new FlowCache();
            this.state.put(FLOW_CACHE, flowCache);
        }

        reroutedFlows.clear();
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
    public void doWork(Tuple tuple) {

        if (CtrlAction.boltHandlerEntrance(this, tuple))
            return;

        logger.trace("State before: {}", state);

        String json = tuple.getString(0);

        /*
          (carmine) Hack Alert
          1) merged two kafka topics into one;
          2) previous logic used topic source to determine how to parse the message
          3) new logic tries to parse it one way, then the next. Slightly inefficient.
         */
        // TODO: Eliminate the inefficiency introduced through the hack
        try {
            logger.info("Received cache data={}", tuple);
            BaseMessage bm = MAPPER.readValue(json, BaseMessage.class);
            if (bm instanceof InfoMessage) {
                InfoMessage message = (InfoMessage) bm;
                String correlationId = message.getCorrelationId();
                InfoData data = message.getData();

                if (data instanceof NetworkInfoData) {
                    logger.debug("Storage content message {}", json);
                    handleNetworkDump(correlationId, (NetworkInfoData) data, tuple);
                } else if (!isReceivedCacheInfo) {
                    logger.debug("Cache message fail due bolt not initialized: "
                                    + "component={}, stream={}, tuple={}",
                            tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple);
                } else if (data instanceof SwitchInfoData) {
                    logger.info("Cache update switch info data: {}", data);
                    handleSwitchEvent((SwitchInfoData) data, tuple);

                } else if (data instanceof IslInfoData) {
                    logger.info("Cache update isl info data: {}", data);
                    handleIslEvent((IslInfoData) data, tuple);

                } else if (data instanceof PortInfoData) {
                    logger.info("Cache update port info data: {}", data);
                    handlePortEvent((PortInfoData) data, tuple);

                } else if (data instanceof FlowInfoData) {
                    logger.info("Cache update flow data: {}", data);

                    FlowInfoData flowData = (FlowInfoData) data;
                    handleFlowEvent(flowData, tuple);
                } else if (data instanceof NetworkTopologyChange) {
                    logger.info("Switch flows reroute request");

                    NetworkTopologyChange topologyChange = (NetworkTopologyChange) data;
                    handleNetworkTopologyChangeEvent(topologyChange, tuple);
                } else {
                    logger.error("Skip undefined info data type {}", json);
                }
            } else {
                logger.error("Skip undefined message type {}", json);
            }

        } catch (CacheException exception) {
            logger.error("Could not process message {}", tuple, exception);

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);
        } finally {
            if (isReceivedCacheInfo) {
                outputCollector.ack(tuple);
            } else {
                outputCollector.fail(tuple);
            }
        }

        logger.trace("State after: {}", state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTick(Tuple tuple) {
        // FIXME(dbogun): tick only once, because timePassed never reset
        if (timePassed == discoveryInterval) {
            Values values = getNetworkRequest();
            if (values != null) {
                outputCollector.emit(StreamType.TPE.toString(), tuple, values);
            } else {
                logger.error("Could not send network cache request");
            }
        }
        if (timePassed <= discoveryInterval) {
            timePassed += 1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declareStream(StreamType.TPE.toString(), AbstractTopology.fieldMessage);
        output.declareStream(StreamType.WFM_DUMP.toString(), AbstractTopology.fieldMessage);
        output.declareStream(StreamType.OFE.toString(), AbstractTopology.fieldMessage);
        // FIXME(dbogun): use proper tuple format
        output.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    private void handleNetworkDump(String correlationId, NetworkInfoData data, Tuple tuple) {
        if (!dumpRequestCorrelationId.equals(correlationId)) {
            logger.info(
                    "Ignore network dump with mismatch correlation id (expect: {}, got: {})",
                    dumpRequestCorrelationId, correlationId);
            return;
        }

        logger.info("Fill network state {}", data);
        data.getSwitches().forEach(networkCache::createOrUpdateSwitch);
        data.getIsls().forEach(networkCache::createOrUpdateIsl);

        logger.info("Load flows {}", data.getFlows().size());
        data.getFlows().forEach(flowCache::putFlow);

        logger.info("Loaded flows {}", flowCache);
        emitRestoreCommands(data.getFlows(), tuple);

        logger.info("Flows restore commands sent");

        ChunkDescriptor chunk = data.getChunk();
        if (chunk != null) {
            logger.info(
                    "Got network dump chunk: {} of {}",
                    chunk.getCurrent(), chunk.getTotal());

            if (dumpRequestUnprocessedChunks == null) {
                dumpRequestUnprocessedChunks = new HashSet<>();
                for (int idx = 1; idx <= chunk.getTotal(); idx += 1) {
                    dumpRequestUnprocessedChunks.add(idx);
                }
            }

            dumpRequestUnprocessedChunks.remove(chunk.getCurrent());
            logger.debug("Unprocessed network dump chunks: {}", dumpRequestUnprocessedChunks);

            if (dumpRequestUnprocessedChunks.size() == 0) {
                logger.info("All network dump chunks are processed, {} is ready to process requests", this.getClass().getName());
                isReceivedCacheInfo = true;
            }
        } else {
            logger.info("Got network dump not sliced on chunks");
            isReceivedCacheInfo = true;
        }
    }

    private void handleSwitchEvent(SwitchInfoData sw, Tuple tuple) throws IOException {
        logger.info("State update switch {} message {}", sw.getSwitchId(), sw.getState());
        Set<ImmutablePair<Flow, Flow>> affectedFlows;

        switch (sw.getState()) {

            case ADDED:
            case ACTIVATED:
                onSwitchUp(sw);
                break;

            case REMOVED:
            case DEACTIVATED:
                if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
                    networkCache.updateSwitch(sw);
                }

                affectedFlows = flowCache.getActiveFlowsWithAffectedPath(sw.getSwitchId());
                String reason = String.format("switch %s is %s", sw.getSwitchId(), sw.getState());
                emitRerouteCommands(affectedFlows, tuple, UUID.randomUUID().toString(), FlowOperation.UPDATE, reason);
                break;

            case CACHED:
                break;
            case CHANGED:
                break;

            default:
                logger.warn("Unknown state update switch info message");
                break;
        }
    }

    private void handleIslEvent(IslInfoData isl, Tuple tuple) {
        logger.info("State update isl {} message cached {}", isl.getId(), isl.getState());
        Set<ImmutablePair<Flow, Flow>> affectedFlows;

        switch (isl.getState()) {
            case DISCOVERED:
                if (networkCache.cacheContainsIsl(isl.getId())) {
                    networkCache.updateIsl(isl);
                } else {
                    networkCache.createIsl(isl);
                }
                break;

            case FAILED:
                try {
                    networkCache.deleteIsl(isl.getId());
                } catch (CacheException exception) {
                    logger.warn("{}:{}", exception.getErrorMessage(), exception.getErrorDescription());
                }

                affectedFlows = flowCache.getActiveFlowsWithAffectedPath(isl);
                String reason = String.format("isl %s FAILED", isl.getId());
                emitRerouteCommands(affectedFlows, tuple, UUID.randomUUID().toString(),
                        FlowOperation.UPDATE, reason);
                break;

            case OTHER_UPDATE:
                break;

            case CACHED:
                break;

            default:
                logger.warn("Unknown state update isl info message");
                break;
        }
    }

    private void handlePortEvent(PortInfoData port, Tuple tuple) {
        logger.info("State update port {}_{} message cached {}", port.getSwitchId(), port.getPortNo(), port.getState());

        switch (port.getState()) {
            case DOWN:
            case DELETE:
                Set<ImmutablePair<Flow, Flow>> affectedFlows = flowCache.getActiveFlowsWithAffectedPath(port);
                String reason = String.format("port %s_%s is %s", port.getSwitchId(), port.getPortNo(), port.getState());
                emitRerouteCommands(affectedFlows, tuple, UUID.randomUUID().toString(), FlowOperation.UPDATE, reason);
                break;

            case UP:
            case ADD:
                break;

            case OTHER_UPDATE:
            case CACHED:
                break;

            default:
                logger.warn("Unknown state update isl info message");
                break;
        }
    }

    private void handleNetworkTopologyChangeEvent(NetworkTopologyChange topologyChange, Tuple tuple) {
        Set<ImmutablePair<Flow, Flow>> affectedFlows;

        switch (topologyChange.getType()) {
            case ENDPOINT_DROP:
                // TODO(surabujin): need implementation
                return;

            case ENDPOINT_ADD:
                affectedFlows = getFlowsForRerouting(topologyChange);
                break;

            default:
                logger.error("Unhandled reroute type: {}", topologyChange.getType());
                return;
        }
        String reason = String.format("network topology change  %s_%s is %s",
                topologyChange.getSwitchId(), topologyChange.getPortNumber(),
                topologyChange.getType());
        emitRerouteCommands(affectedFlows, tuple, UUID.randomUUID().toString(),
                FlowOperation.UPDATE, reason);
    }

    private void emitFlowMessage(InfoData data, Tuple tuple, String correlationId) throws IOException {
        Message message = new InfoMessage(data, System.currentTimeMillis(),
                correlationId, Destination.TOPOLOGY_ENGINE);
        outputCollector.emit(StreamType.TPE.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.info("Flow command message sent");
    }

    private void emitFlowCrudMessage(InfoData data, Tuple tuple, String correlationId) throws IOException {
        Message message = new InfoMessage(data, System.currentTimeMillis(),
                correlationId, Destination.WFM);
        outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.info("Flow command message sent");
    }

    private void emitRestoreCommands(Set<ImmutablePair<Flow, Flow>> flows, Tuple tuple) {
        if (flows != null) {

            ResourceCache resourceCache = new ResourceCache();
            for (ImmutablePair<Flow, Flow> flow : flows) {
                resourceCache.allocateFlow(flow);
            }

            for (ImmutablePair<Flow, Flow> flow : flows) {
                try {
                    FlowRestoreRequest request = new FlowRestoreRequest(
                            flowCache.buildFlow(flow.getLeft(), new ImmutablePair<>(null, null), resourceCache));
                    resourceCache.deallocateFlow(flow);

                    Values values = new Values(Utils.MAPPER.writeValueAsString(new CommandMessage(
                            request, System.currentTimeMillis(), UUID.randomUUID().toString(), Destination.WFM)));
                    outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, values);

                    logger.info("Flow {} restore command message sent", flow.getLeft().getFlowId());
                } catch (JsonProcessingException exception) {
                    logger.error("Could not format flow restore request by flow={}", flow, exception);
                }
            }
        }
    }

    private void emitRerouteCommands(Set<ImmutablePair<Flow, Flow>> flows, Tuple tuple,
                                     String correlationId, FlowOperation operation, String reason) {
        for (ImmutablePair<Flow, Flow> flow : flows) {
            try {
                flow.getLeft().setState(FlowState.DOWN);
                flow.getRight().setState(FlowState.DOWN);
                FlowRerouteRequest request = new FlowRerouteRequest(flow.getLeft(), operation);

                Values values = new Values(Utils.MAPPER.writeValueAsString(new CommandMessage(
                        request, System.currentTimeMillis(), correlationId, Destination.WFM)));
                outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, values);

                logger.warn("Flow {} reroute command message sent with correlationId {} reason {}",
                        flow.getLeft().getFlowId(), correlationId, reason);
            } catch (JsonProcessingException exception) {
                logger.error("Could not format flow reroute request by flow={}", flow, exception);
            }
        }
    }

    private Values getNetworkRequest() {
        Values values = null;

        dumpRequestCorrelationId = UUID.randomUUID().toString();
        try {
            CommandMessage command = new CommandMessage(new NetworkCommandData(),
                    System.currentTimeMillis(), dumpRequestCorrelationId, Destination.TOPOLOGY_ENGINE);
            values = new Values(Utils.MAPPER.writeValueAsString(command));
        } catch (IOException exception) {
            logger.error("Could not serialize network cache request", exception);
        }

        return values;
    }

    private void onSwitchUp(SwitchInfoData sw) throws IOException {
        logger.info("Switch {} is {}", sw.getSwitchId(), sw.getState().getType());
        if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
            networkCache.updateSwitch(sw);
        } else {
            networkCache.createSwitch(sw);
        }
    }

    private void handleFlowEvent(FlowInfoData flowData, Tuple tuple) throws IOException {
        switch (flowData.getOperation()) {
            case PUSH:
            case PUSH_PROPAGATE:
                logger.debug("Flow {} message received: {}", flowData.getOperation(), flowData);
                flowCache.putFlow(flowData.getPayload());
                logger.info("Flow {} message processed: {}", flowData.getOperation(), flowData);
                // do not emit to TPE .. NB will send directly
                break;

            case UNPUSH:
            case UNPUSH_PROPAGATE:
                logger.debug("Flow {} message received: {}", flowData.getOperation(), flowData);
                String flowsId2 = flowData.getPayload().getLeft().getFlowId();
                flowCache.removeFlow(flowsId2);
                reroutedFlows.remove(flowsId2);
                logger.info("Flow {} message processed: {}", flowData.getOperation(), flowData);
                break;


            case CREATE:
                // TODO: This should be more lenient .. in case of retries
                logger.debug("Flow create message received: {}", flowData);
                flowCache.putFlow(flowData.getPayload());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.info("Flow create message sent: {}", flowData);
                break;

            case DELETE:
                // TODO: This should be more lenient .. in case of retries
                logger.debug("Flow remove message received: {}", flowData);
                String flowsId = flowData.getPayload().getLeft().getFlowId();
                flowCache.removeFlow(flowsId);
                reroutedFlows.remove(flowsId);
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.info("Flow remove message sent: {}", flowData);
                break;

            case UPDATE:
                logger.info("Flow update message received: {}", flowData);
                processFlowUpdate(flowData.getPayload().getLeft());
                // TODO: This should be more lenient .. in case of retries
                flowCache.putFlow(flowData.getPayload());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.info("Flow update message sent: {}", flowData);
                break;

            case STATE:
                flowCache.putFlow(flowData.getPayload());
                logger.info("Flow state changed: {}", flowData);
                break;

            case CACHE:
                logger.debug("Sync flow cache message received: {}", flowData);
                if(flowData.getPayload() != null) {
                    flowCache.putFlow(flowData.getPayload());
                } else {
                    flowCache.removeFlow(flowData.getFlowId());
                }
                break;

            default:
                logger.warn("Skip undefined flow operation {}", flowData);
                break;
        }
    }

    /**
     * Checks whether flow path was changed. If so we store switches through which flow was built.
     * @param flow flow to be processed
     */
    private void processFlowUpdate(Flow flow) {
        final String flowId = flow.getFlowId();
        ImmutablePair<Flow, Flow> cachedFlow = flowCache.getFlow(flowId);
        //check whether flow path was changed
        if (!flowPathWasChanges(cachedFlow.getLeft(), flow)) {
            Set<PathNode> affectedNodes = getAffectedNodes(cachedFlow.getLeft().getFlowPath().getPath(),
                    flow.getFlowPath().getPath());
            logger.debug("Saving flow {} new path for possibility to rollback it", flow.getFlowId());
            affectedNodes.stream()
                    .map(PathNode::getSwitchId)
                    //we need to store only deactivated switches, so when they come up again we will be able
                    //to try to reroute flows through this switches
                    .filter(switchId -> !networkCache.switchIsOperable(switchId))
                    .forEach(switchId -> {
                        Set<String> flows = reroutedFlows.get(switchId);
                        if (CollectionUtils.isEmpty(flows)) {
                            reroutedFlows.put(switchId, Sets.newHashSet(flowId));
                        } else {
                            flows.add(flowId);
                        }
                    });
        }
    }

    private boolean flowPathWasChanges(Flow initial, Flow updated) {
        return initial.getFlowPath().getPath().equals(updated.getFlowPath().getPath());
    }

    /**
     * Returns difference in switches between previous and current path.
     * @param cachedPath previous flow path.
     * @param updatedPath current flow path.
     * @return switches from previous path, that not involved in the updated path
     */
    private Set<PathNode> getAffectedNodes(List<PathNode> cachedPath, List<PathNode> updatedPath) {
        Set<PathNode> prevPath = new HashSet<>(cachedPath);
        prevPath.removeAll(updatedPath);
        return prevPath;
    }

    private Set<ImmutablePair<Flow, Flow>> getFlowsForRerouting(NetworkTopologyChange rerouteData) {
        Set<ImmutablePair<Flow, Flow>> inactiveFlows = flowCache.dumpFlows().stream()
                .filter(flow -> FlowState.DOWN.equals(flow.getLeft().getState()))
                .collect(Collectors.toSet());

        Set<ImmutablePair<Flow, Flow>> transitFlows = getTransitFlowsPreviouslyInstalled(rerouteData.getSwitchId());
        return Sets.union(inactiveFlows, transitFlows);

    }

    /**
     * Returns flows where specific switch was involved as transit switch before.
     * @param switchId switch id to be searched.
     * @return list of flows.
     */
    private Set<ImmutablePair<Flow, Flow>> getTransitFlowsPreviouslyInstalled(String switchId) {
        Set<String> flowIds = reroutedFlows.remove(switchId);
        if (!CollectionUtils.isEmpty(flowIds)) {
            logger.debug("Found transit flows ({}) for switch {}", StringUtils.join(flowIds, ", "), switchId);
            return flowIds.stream()
                    .map(flowId -> flowCache.getFlow(flowId))
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public AbstractDumpState dumpState() {
        NetworkDump networkDump = new NetworkDump(
                networkCache.dumpSwitches(), networkCache.dumpIsls());
        FlowDump flowDump = new FlowDump(flowCache.dumpFlows());
        return new CacheBoltState(networkDump, flowDump);
    }

    @VisibleForTesting
    @Override
    public void clearState() {
        logger.info("State clear request from test");
        initState(new InMemoryKeyValueState<>());
        isReceivedCacheInfo = false;
        timePassed = 0;
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(String switchId) {
        // Not implemented
        NetworkDump networkDump = new NetworkDump(
                new HashSet<>(),
                new HashSet<>());
        FlowDump flowDump = new FlowDump(new HashSet<>());
        return new CacheBoltState(networkDump, flowDump);
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
