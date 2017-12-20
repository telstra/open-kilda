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
import static org.openkilda.wfm.topology.flow.StreamType.REROUTE;
import static org.openkilda.wfm.topology.flow.StreamType.RESTORE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
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
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.cache.Cache;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.NetworkCache;
import org.openkilda.pce.cache.ResourceCache;
import org.openkilda.wfm.OFEMessageUtils;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.cache.service.CacheWarmingService;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private static final Logger logger = LogManager.getLogger(CacheBolt.class);

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

    private CacheWarmingService cacheWarmingService;

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

        cacheWarmingService = new CacheWarmingService(networkCache);
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
        String source = tuple.getSourceComponent();

        /*
          (carmine) Hack Alert
          1) merged two kafka topics into one;
          2) previous logic used topic source to determine how to parse the message
          3) new logic tries to parse it one way, then the next. Slightly inefficient.
         */
        // TODO: Eliminate the inefficiency introduced through the hack
        try {
            logger.info("Request tuple={}", tuple);
            BaseMessage bm = MAPPER.readValue(json, BaseMessage.class);
            if (bm instanceof InfoMessage) {
                InfoMessage info = (InfoMessage) bm;
                if (Destination.WFM_CACHE == info.getDestination()){
                    logger.debug("Storage content message {}", json);
                    handleNetworkDump(info.getData(), tuple);
                }
            } else if (bm instanceof InfoData) {
                InfoData data = (InfoData) bm;
                logger.debug("Cache update info data", data);

                if (data instanceof SwitchInfoData) {
                    logger.info("Cache update switch info data: {}", data);

                    emitNetworkMessage(data, tuple, Utils.SYSTEM_CORRELATION_ID);
                    handleSwitchEvent((SwitchInfoData) data, tuple);

                } else if (data instanceof IslInfoData) {
                    logger.info("Cache update isl info data: {}", data);

                    emitNetworkMessage(data, tuple, Utils.SYSTEM_CORRELATION_ID);
                    handleIslEvent((IslInfoData) data, tuple);

                } else if (data instanceof PortInfoData) {
                    logger.info("Cache update port info data: {}", data);

                    emitNetworkMessage(data, tuple, Utils.SYSTEM_CORRELATION_ID);
                    handlePortEvent((PortInfoData) data, tuple);

                } else if (data instanceof FlowInfoData) {
                    logger.info("Cache update info data: {}", data);

                    FlowInfoData flowData = (FlowInfoData) data;
                    handleFlowEvent(flowData, tuple);
                } else {
                    logger.debug("Skip undefined info data type {}", json);
                }
            } else {
                logger.debug("Skip undefined message type {}", json);
            }

        } catch (CacheException exception) {
            logger.error("Could not process message {}", tuple, exception);

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } finally {
            logger.debug("Cache message ack: component={}, stream={}, tuple={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple);

            outputCollector.ack(tuple);
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

    private void handleNetworkDump(InfoData info, Tuple tuple) {
        if (info instanceof NetworkInfoData) {
            NetworkInfoData data = (NetworkInfoData) info;
            logger.info("Fill network state {}", data);
            emitRestoreCommands(data.getFlows(), tuple);
            logger.info("Flows restore commands sent");
        } else if (info instanceof SwitchInfoData) {
            SwitchInfoData sw = (SwitchInfoData) info;
            logger.info("Cached switch with id {}", sw.getSwitchId());
            networkCache.createSwitch(sw);
        } else if (info instanceof IslInfoData) {
            IslInfoData isl = (IslInfoData) info;
            logger.info("Cached isl with id {}", isl.getId());
            networkCache.createIsl(isl);
        } else if (info instanceof FlowInfoData) {
            FlowInfoData flow = (FlowInfoData) info;
            logger.info("Cached flow with id {}", flow.getFlowId());
            flowCache.putFlow(flow.getPayload());
            cacheWarmingService.addPredefinedFlow(flow.getPayload());
        } else {
            logger.warn("Incorrect network state {}", info);
        }
    }

    private void handleSwitchEvent(SwitchInfoData sw, Tuple tuple) {
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

                affectedFlows = flowCache.getFlowsWithAffectedPath(sw.getSwitchId());
                emitRerouteCommands(affectedFlows, tuple, "SWITCH", FlowOperation.UPDATE);

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
                affectedFlows = flowCache.dumpFlows().stream()
                        .filter(flow -> FlowState.DOWN.equals(flow.getLeft().getState()))
                        .collect(Collectors.toSet());
                emitRerouteCommands(affectedFlows, tuple, "ISL", FlowOperation.UPDATE);
                break;

            case FAILED:
                try {
                    networkCache.deleteIsl(isl.getId());
                } catch (CacheException exception) {
                    logger.warn("{}:{}", exception.getErrorMessage(), exception.getErrorDescription());
                }
                affectedFlows = flowCache.getFlowsWithAffectedPath(isl);
                emitRerouteCommands(affectedFlows, tuple, "ISL", FlowOperation.UPDATE);
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

    private void emitNetworkMessage(InfoData data, Tuple tuple, String correlationId) throws IOException {
        Message message = new InfoMessage(data, System.currentTimeMillis(),
                correlationId, Destination.TOPOLOGY_ENGINE);
        outputCollector.emit(StreamType.TPE.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.info("Network info message sent");
    }

    private void emitFlowMessage(InfoData data, Tuple tuple, String correlationId) throws IOException {
        Message message = new InfoMessage(data, System.currentTimeMillis(),
                correlationId, Destination.TOPOLOGY_ENGINE);
        outputCollector.emit(StreamType.TPE.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.info("Flow command message sent");
    }

    private void emitRestoreCommands(Set<ImmutablePair<Flow, Flow>> flows, Tuple tuple) {
        String rerouteCorrelationId = RESTORE.toString();

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
                            request, System.currentTimeMillis(), rerouteCorrelationId, Destination.WFM)));
                    outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, values);

                    logger.info("Flow {} restore command message sent", flow.getLeft().getFlowId());
                } catch (JsonProcessingException exception) {
                    logger.error("Could not format flow restore request by flow={}", flow, exception);
                }
            }
        }
    }

    private void emitRerouteCommands(Set<ImmutablePair<Flow, Flow>> flows, Tuple tuple,
                                     String correlationId, FlowOperation operation) {
        String rerouteCorrelationId = String.format("%s-%s", correlationId, REROUTE.toString());

        for (ImmutablePair<Flow, Flow> flow : flows) {
            try {
                flow.getLeft().setState(FlowState.DOWN);
                flow.getRight().setState(FlowState.DOWN);
                FlowRerouteRequest request = new FlowRerouteRequest(flow.getLeft(), operation);

                Values values = new Values(Utils.MAPPER.writeValueAsString(new CommandMessage(
                        request, System.currentTimeMillis(), rerouteCorrelationId, Destination.WFM)));
                outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, values);

                logger.info("Flow {} reroute command message sent", flow.getLeft().getFlowId());
            } catch (JsonProcessingException exception) {
                logger.error("Could not format flow reroute request by flow={}", flow, exception);
            }
        }
    }

    private Values getNetworkRequest() {
        Values values = null;

        try {
            CommandMessage command = new CommandMessage(new NetworkCommandData(),
                    System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
            values = new Values(Utils.MAPPER.writeValueAsString(command));
        } catch (IOException exception) {
            logger.error("Could not serialize network cache request", exception);
        }

        return values;
    }

    private void onSwitchUp(SwitchInfoData sw) {
        logger.info("Switch {} is up", sw.getSwitchId());
        if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
            SwitchState prevState = networkCache.getSwitch(sw.getSwitchId()).getState();
            if (prevState == SwitchState.CACHED && sw.getState().isActive()) {
                String switchId = sw.getSwitchId();
                List<PortInfoData> ports = cacheWarmingService.getPortsForDiscovering(switchId);
                logger.info("Found {} links for discovery", ports.size());
                sendPortCachedEvent(ports);

                List<? extends CommandData> commands = cacheWarmingService.getFlowCommands(switchId);
                sendFlowCommands(commands);
            }
            networkCache.updateSwitch(sw);
        } else {
            networkCache.createSwitch(sw);
        }

    }

    private void handleFlowEvent(FlowInfoData flowData, Tuple tuple) throws IOException {
        switch (flowData.getOperation()) {
            case CREATE:
                flowCache.putFlow(flowData.getPayload());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.info("Flow create message sent: {}", flowData);
                break;

            case DELETE:
                flowCache.removeFlow(flowData.getPayload().getLeft().getFlowId());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.info("Flow remove message sent: {}", flowData);
                break;

            case UPDATE:
                flowCache.putFlow(flowData.getPayload());
                emitFlowMessage(flowData, tuple, flowData.getCorrelationId());
                logger.info("Flow update message sent: {}", flowData);
                break;

            case STATE:
                flowCache.putFlow(flowData.getPayload());
                logger.info("Flow state changed: {}", flowData);
                break;

            case CACHE:
                break;

            default:
                logger.warn("Skip undefined flow operation {}", flowData);
                break;
        }
    }

    private void sendFlowCommands(List<? extends CommandData> commands) {
        commands.forEach(command -> {
            try {
                outputCollector.emit(StreamType.WFM_DUMP.toString(), new Values(MAPPER.writeValueAsString(command)));
                logger.debug("Flow command request sent from CacheBolt: {}", command);
            } catch (JsonProcessingException e) {
                logger.error("Error during serializing CommandData", e);
            }
        });
    }

    private void sendPortCachedEvent(List<PortInfoData> ports) {
        ports.forEach(portInfoData -> {
            String correlationId = UUID.randomUUID().toString();
            InfoMessage message = new InfoMessage(portInfoData, System.currentTimeMillis(), correlationId,
                    Destination.WFM);
            try {
                outputCollector.emit(StreamType.OFE.toString(), new Values(MAPPER.writeValueAsString(message)));
                logger.info("Isl discovery request sent from CacheBolt with correlationId {}", correlationId);
            } catch (JsonProcessingException e) {
                logger.error("Error during serializing PortInfoData", e);
            }
        });
    }

    @Override
    public AbstractDumpState dumpState() {
        NetworkDump networkDump = new NetworkDump(
                networkCache.dumpSwitches(), networkCache.dumpIsls());
        FlowDump flowDump = new FlowDump(flowCache.dumpFlows());
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
