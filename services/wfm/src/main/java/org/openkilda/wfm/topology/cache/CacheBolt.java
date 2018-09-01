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

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.CacheBoltState;
import org.openkilda.messaging.ctrl.state.FlowDump;
import org.openkilda.messaging.ctrl.state.NetworkDump;
import org.openkilda.messaging.ctrl.state.ResorceCacheBoltState;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.NetworkTopologyChange;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.cache.Cache;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.NetworkCache;
import org.openkilda.pce.provider.Auth;
import org.openkilda.pce.provider.NeoDriver;
import org.openkilda.pce.provider.PathComputer;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.share.utils.PathComputerFlowFetcher;
import org.openkilda.wfm.topology.AbstractTopology;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CacheBolt
        extends BaseStatefulBolt<InMemoryKeyValueState<String, Cache>>
        implements ICtrlBolt {
    public static final String STREAM_ID_CTRL = "ctrl";
    public static final String FLOW_ID_FIELD = "flowId";
    public static final String CORRELATION_ID_FIELD = "correlationId";

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

    private final Auth pathComputerAuth;

    private TopologyContext context;
    private OutputCollector outputCollector;

    CacheBolt(Auth pathComputerAuth) {
        this.pathComputerAuth = pathComputerAuth;
    }

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

        logger.info("Request initial network state");

        final PathComputer pathComputer = new NeoDriver(pathComputerAuth.getDriver());
        initFlowCache(pathComputer);
        initNetwork(pathComputer);
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
        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }
        logger.trace("State before: {}", state);

        String json = tuple.getString(0);
        String source = tuple.getSourceComponent();

        /*
          (carmine) Hack Alert
          1) merged two kafka topics into one;
          2) previous logic used topic source to determine how to parse the message
          3) new logic tries to parse it one way, then the next. Slightly inefficient.
         */
        // TODO: Eliminate the inefficiency introduced through the hack
        try {
            BaseMessage bm = MAPPER.readValue(json, BaseMessage.class);
            if (bm instanceof InfoMessage) {
                InfoMessage message = (InfoMessage) bm;
                InfoData data = message.getData();

                if (data instanceof SwitchInfoData) {
                    logger.info("Cache update switch info data: {}", data);
                    handleSwitchEvent((SwitchInfoData) data, tuple, message.getCorrelationId());

                } else if (data instanceof IslInfoData) {
                    handleIslEvent((IslInfoData) data, tuple, message.getCorrelationId());

                } else if (data instanceof PortInfoData) {
                    handlePortEvent((PortInfoData) data, tuple, message.getCorrelationId());

                } else if (data instanceof FlowInfoData) {

                    FlowInfoData flowData = (FlowInfoData) data;
                    handleFlowEvent(flowData, tuple, message.getCorrelationId());
                } else if (data instanceof NetworkTopologyChange) {
                    logger.debug("Switch flows reroute request");

                    NetworkTopologyChange topologyChange = (NetworkTopologyChange) data;
                    handleNetworkTopologyChange(topologyChange, tuple, message.getCorrelationId());
                } else {
                    logger.warn("Skip undefined info data type {}", json);
                }
            } else {
                logger.warn("Skip undefined message type {}", json);
            }

        } catch (CacheException exception) {
            logger.error("Could not process message {}", tuple, exception);
        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);
        }

        logger.trace("State after: {}", state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declareStream(StreamType.TPE.toString(), AbstractTopology.fieldMessage);
        output.declareStream(StreamType.WFM_REROUTE.toString(), new Fields(FLOW_ID_FIELD, CORRELATION_ID_FIELD));
        output.declareStream(StreamType.OFE.toString(), AbstractTopology.fieldMessage);
        // FIXME(dbogun): use proper tuple format
        output.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    private void handleSwitchEvent(SwitchInfoData sw, Tuple tuple, String correlationId) throws IOException {
        logger.debug("State update switch {} message {}", sw.getSwitchId(), sw.getState());
        Set<FlowPair<Flow, Flow>> affectedFlows;

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

                // (crimi - 2018.04.17) - eliminating taking action on Switch down events ..
                // primarily because floodlight can regularly drop a connection to the switch (or
                // vice versa) and a new connection is made almost immediately. Essentially, a flap.
                // Rather than reroute here .. what to see if an ISL goes down.  This introduces a
                // longer delay .. but a necessary dampening affect.  The better solution
                // is to kick of an immediate probe if we get such an event .. and the probe
                // should confirm what is really happening.
                //affectedFlows = flowCache.getActiveFlowsWithAffectedPath(sw.getSwitchId());
                //String reason = String.format("switch %s is %s", sw.getSwitchId(), sw.getState());
                //emitRerouteCommands(affectedFlows, tuple, correlationId, FlowOperation.UPDATE, reason);
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

    private void handleIslEvent(IslInfoData isl, Tuple tuple, String correlationId) {
        logger.debug("State update isl {} message cached {}", isl.getId(), isl.getState());
        Set<FlowPair<Flow, Flow>> affectedFlows;

        switch (isl.getState()) {
            case DISCOVERED:
                if (networkCache.cacheContainsIsl(isl.getId())) {
                    networkCache.updateIsl(isl);
                } else {
                    if (isl.isSelfLooped()) {
                        logger.warn("Skipped self-looped ISL: {}", isl);
                    } else {
                        networkCache.createIsl(isl);
                    }
                }
                break;

            case FAILED:
            case MOVED:
                try {
                    networkCache.deleteIsl(isl.getId());
                } catch (CacheException exception) {
                    logger.warn("{}:{}", exception.getErrorMessage(), exception.getErrorDescription());
                }

                affectedFlows = flowCache.getActiveFlowsWithAffectedPath(isl);
                String reason = String.format("isl %s FAILED", isl.getId());
                emitRerouteCommands(tuple, affectedFlows, correlationId, reason);
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

    private void handlePortEvent(PortInfoData port, Tuple tuple, String correlationId) {
        logger.debug("State update port {}_{} message cached {}",
                port.getSwitchId(), port.getPortNo(), port.getState());

        switch (port.getState()) {
            case DOWN:
            case DELETE:
                Set<FlowPair<Flow, Flow>> affectedFlows = flowCache.getActiveFlowsWithAffectedPath(port);
                String reason = String.format("port %s_%s is %s",
                        port.getSwitchId(), port.getPortNo(), port.getState());
                emitRerouteCommands(tuple, affectedFlows, correlationId, reason);
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

    private void handleNetworkTopologyChange(NetworkTopologyChange topologyChange, Tuple tuple, String correlationId) {
        Set<FlowPair<Flow, Flow>> affectedFlows;

        switch (topologyChange.getType()) {
            case ENDPOINT_DROP:
                // TODO(surabujin): need implementation
                return;

            case ENDPOINT_ADD:
                affectedFlows = getFlowsForRerouting();
                break;

            default:
                logger.error("Unhandled reroute type: {}", topologyChange.getType());
                return;
        }
        String reason = String.format("network topology change  %s_%s is %s",
                topologyChange.getSwitchId(), topologyChange.getPortNumber(),
                topologyChange.getType());
        emitRerouteCommands(tuple, affectedFlows, correlationId, reason);
    }

    private void emitFlowMessage(InfoData data, Tuple tuple, String correlationId) throws IOException {
        Message message = new InfoMessage(data, System.currentTimeMillis(),
                correlationId, Destination.TOPOLOGY_ENGINE);
        outputCollector.emit(StreamType.TPE.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.debug("Flow command message sent");
    }

    private void emitRerouteCommands(Tuple input, Set<FlowPair<Flow, Flow>> flows,
                                     String initialCorrelationId, String reason) {
        for (FlowPair<Flow, Flow> flow : flows) {
            final String flowId = flow.getLeft().getFlowId();

            String correlationId = format("%s-%s", initialCorrelationId, flowId);

            Values values = new Values(flowId, correlationId);
            outputCollector.emit(StreamType.WFM_REROUTE.toString(), input, values);

            flow.getLeft().setState(FlowState.DOWN);
            flow.getRight().setState(FlowState.DOWN);

            logger.warn("Flow {} reroute command message sent with correlationId {}, reason {}",
                    flowId, correlationId, reason);
        }
    }

    private void onSwitchUp(SwitchInfoData sw) throws IOException {
        logger.info("Switch {} is {}", sw.getSwitchId(), sw.getState().getType());
        if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
            networkCache.updateSwitch(sw);
        } else {
            networkCache.createSwitch(sw);
        }
    }

    private void handleFlowEvent(FlowInfoData flowData, Tuple tuple, String correlationId) throws IOException {
        switch (flowData.getOperation()) {
            case PUSH:
            case PUSH_PROPAGATE:
                logger.debug("Flow {} message received: {}, correlationId: {}", flowData.getOperation(), flowData,
                        correlationId);
                flowCache.putFlow(flowData.getPayload());
                // do not emit to TPE .. NB will send directly
                break;

            case UNPUSH:
            case UNPUSH_PROPAGATE:
                logger.trace("Flow {} message received: {}, correlationId: {}", flowData.getOperation(), flowData,
                        correlationId);
                String flowsId2 = flowData.getPayload().getLeft().getFlowId();
                flowCache.removeFlow(flowsId2);
                logger.debug("Flow {} message processed: {}, correlationId: {}", flowData.getOperation(), flowData,
                        correlationId);
                break;


            case CREATE:
                // TODO: This should be more lenient .. in case of retries
                logger.trace("Flow create message received: {}, correlationId: {}", flowData, correlationId);
                flowCache.putFlow(flowData.getPayload());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.debug("Flow create message sent: {}, correlationId: {}", flowData, correlationId);
                break;

            case DELETE:
                // TODO: This should be more lenient .. in case of retries
                logger.trace("Flow remove message received: {}, correlationId: {}", flowData, correlationId);
                String flowsId = flowData.getPayload().getLeft().getFlowId();
                flowCache.removeFlow(flowsId);
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.debug("Flow remove message sent: {}, correlationId: {} ", flowData, correlationId);
                break;

            case UPDATE:
                logger.trace("Flow update message received: {}, correlationId: {}", flowData, correlationId);
                // TODO: This should be more lenient .. in case of retries
                flowCache.putFlow(flowData.getPayload());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.debug("Flow update message sent: {}, correlationId: {}", flowData, correlationId);
                break;

            case STATE:
                flowCache.putFlow(flowData.getPayload());
                logger.debug("Flow state changed: {}, correlationId: {}", flowData, correlationId);
                break;

            case CACHE:
                logger.debug("Sync flow cache message received: {}, correlationId: {}", flowData, correlationId);
                if (flowData.getPayload() != null) {
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

    private Set<FlowPair<Flow, Flow>> getFlowsForRerouting() {
        Set<FlowPair<Flow, Flow>> inactiveFlows = flowCache.dumpFlows().stream()
                .filter(flow -> FlowState.DOWN.equals(flow.getLeft().getState()))
                .collect(Collectors.toSet());

        return inactiveFlows;
    }

    private void initNetwork(PathComputer pathComputer) {
        logger.info("Network Cache: Initializing");
        Set<SwitchInfoData> switches = new HashSet<>(pathComputer.getSwitches());
        Set<IslInfoData> links = new HashSet<>(pathComputer.getIsls());

        logger.info("Network Cache: Initializing - {} Switches (size)", switches.size());
        logger.info("Network Cache: Initializing - {} ISLs (size)", links.size());

        //
        // We will call createOrUpdate to ensure we can ignore duplicates.
        //
        // The alternative is to call networkCache::createOrUpdateSwitch / networkCache::createOrUpdateIsl
        // networkCache.load(switches, links);

        switches.forEach(networkCache::createOrUpdateSwitch);

        for (IslInfoData isl : links) {
            try {
                if (isl.isSelfLooped()) {
                    logger.warn("Skipped self-looped ISL: {}", isl);
                } else {
                    networkCache.createOrUpdateIsl(isl);
                }
            } catch (Exception e) {
                logger.error("CacheBolt :: initNetwork :: add ISL caused error --> isl = {} ; Exception = {}", isl, e);
            }
        }

        logger.info("Network Cache: Initialized");
    }

    private void initFlowCache(PathComputer pathComputer) {
        logger.info("Flow Cache: Initializing");
        PathComputerFlowFetcher flowFetcher = new PathComputerFlowFetcher(pathComputer);

        for (BidirectionalFlow bidirectionalFlow : flowFetcher.getFlows()) {
            FlowPair<Flow, Flow> flowPair = new FlowPair<>(
                    bidirectionalFlow.getForward(), bidirectionalFlow.getReverse());
            flowCache.pushFlow(flowPair);
        }
        logger.info("Flow Cache: Initialized");
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
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(SwitchId switchId) {
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

    @Override
    public Optional<AbstractDumpState> dumpResorceCacheState() {
        return Optional.of(new ResorceCacheBoltState(
                flowCache.getAllocatedMeters(),
                flowCache.getAllocatedVlans(),
                flowCache.getAllocatedCookies()));
    }
}
