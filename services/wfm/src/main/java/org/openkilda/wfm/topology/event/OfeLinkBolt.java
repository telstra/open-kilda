/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.event;

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.HeartBeat;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.DeactivateIslInfoData;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Switch;
import org.openkilda.messaging.model.SwitchPort;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.OfeMessageUtils;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.isl.DiscoveryManager;
import org.openkilda.wfm.isl.DummyIIslFilter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.event.OFEventWfmTopologyConfig.DiscoveryConfig;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;
import org.openkilda.wfm.topology.utils.KibanaLogWrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This class is the main class for tracking network topology. The most complicated part of the network topology is
 * links and generating health checks.
 *
 * <p>Because this class / topology acts as a regulator / throttler for what the topology engine
 * receives, it is responsible for passing along changes in the network to the topology engine. In other words, this
 * class acts as a buffer for what the topology engine receives. As such, it needs to send Switch / Port / Link up/down
 * status messages.
 *
 * <p>Regarding Storm's KeyValueState .. it
 * doesn't have a keys() feature .. so there is at this stage only one object in it, which holds hashmaps, etc.
 *
 * <p>Cache warming:
 * For update code in Storm, we need to kill and load the new topology, and bolt loses all internal state. For restore
 * data in bolt we send a message to FL and wait till callback message with network data arrive. We don't process common
 * messages and mark it as fail before that. UML Diagram is here https://github.com/telstra/open-kilda/issues/213 \
 */
public class OfeLinkBolt
        extends AbstractTickStatefulBolt<KeyValueState<String, Object>>
        implements ICtrlBolt {
    private static final Logger logger = LoggerFactory.getLogger(OfeLinkBolt.class);
    private static final KibanaLogWrapper logWrapper = new KibanaLogWrapper(logger);
    private static final int BOLT_TICK_INTERVAL = 1;

    private static final String STREAM_ID_CTRL = "ctrl";
    @VisibleForTesting
    static final String STATE_ID_DISCOVERY = "discovery-manager";
    static final String STATE_ID_UNMANGED_SWITCHES = "unmanaged_switches";
    static final String SPEAKER_DISCO_STREAM = "speaker.disco";
    static final String SPEAKER_STREAM = "speaker";
    static final String NETWORK_TOPOLOGY_CHANGE_STREAM = "network-topology-change";

    private final String islDiscoveryTopic;

    private final int islHealthCheckInterval;
    private final int islHealthCheckTimeout;
    private final int islHealthFailureLimit;
    private final int islKeepRemovedTimeout;
    private final int bfdPortOffset;
    private TopologyContext context;
    private OutputCollector collector;

    private DummyIIslFilter islFilter;
    @VisibleForTesting
    DiscoveryManager discovery;
    private Map<SwitchId, Set<DiscoveryLink>> linksBySwitch;
    private Set<SwitchId> unmanagedSwitches;

    @VisibleForTesting
    State state = State.MAIN;

    /**
     * Default constructor .. default health check frequency
     */
    public OfeLinkBolt(OFEventWfmTopologyConfig config) {
        super(BOLT_TICK_INTERVAL);

        DiscoveryConfig discoveryConfig = config.getDiscoveryConfig();
        islHealthCheckInterval = discoveryConfig.getDiscoveryInterval();
        Preconditions.checkArgument(islHealthCheckInterval > 0,
                "Invalid value for DiscoveryInterval: %s", islHealthCheckInterval);
        islHealthCheckTimeout = discoveryConfig.getDiscoveryTimeout();
        Preconditions.checkArgument(islHealthCheckTimeout > 0,
                "Invalid value for DiscoveryTimeout: %s", islHealthCheckTimeout);
        islHealthFailureLimit = discoveryConfig.getDiscoveryLimit();
        islKeepRemovedTimeout = discoveryConfig.getKeepRemovedIslTimeout();

        islDiscoveryTopic = config.getKafkaSpeakerDiscoTopic();
        bfdPortOffset = config.getBfdPortOffset();
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        islFilter = new DummyIIslFilter();

        this.context = context;
        this.collector = collector;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initState(KeyValueState<String, Object> state) {

        // NB: First time the worker is created this will be null
        // TODO: what happens to state as workers go up or down
        Object payload = state.get(STATE_ID_DISCOVERY);
        if (payload == null) {
            payload = linksBySwitch = new HashMap<>();
            state.put(islDiscoveryTopic, payload);
        } else {
            linksBySwitch = (Map<SwitchId, Set<DiscoveryLink>>) payload;
        }

        Object unmanagedSwitchesFromState = state.get(STATE_ID_UNMANGED_SWITCHES);
        if (unmanagedSwitchesFromState == null) {
            unmanagedSwitches = new HashSet<SwitchId>();
            state.put(STATE_ID_UNMANGED_SWITCHES, unmanagedSwitches);
        } else {
            unmanagedSwitches = (Set<SwitchId>) unmanagedSwitchesFromState;
        }

        // DiscoveryManager counts failures as failed attempts,
        // so we need to convert islHealthCheckTimeout (which is in ticks) into attempts.
        int islConsecutiveFailureLimit = (int) Math.ceil(islHealthCheckTimeout / (float) islHealthCheckInterval);

        discovery = new DiscoveryManager(linksBySwitch, islHealthCheckInterval, islConsecutiveFailureLimit,
                islHealthFailureLimit, islKeepRemovedTimeout);
    }

    /**
     * Send ISL health checks for all links.
     */
    @Override
    protected void doTick(Tuple tuple) {
        if (unmanagedSwitches.isEmpty()) {
            String correlationId = UUID.randomUUID().toString();
            processDiscoveryPlan(tuple, correlationId);
        }
    }

    /**
     * Send network dump request to FL.
     */
    private String sendNetworkRequest(Tuple tuple, String correlationId) {
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER);

        logger.info(
                "Send network dump request (correlation-id: {})",
                correlationId);

        try {
            String json = Utils.MAPPER.writeValueAsString(command);
            collector.emit(SPEAKER_STREAM, tuple, new Values(PAYLOAD, json));
        } catch (JsonProcessingException exception) {
            logger.error("Could not serialize network cache request", exception);
        }

        return correlationId;
    }

    private void processDiscoveryPlan(Tuple tuple, String correlationId) {
        DiscoveryManager.Plan discoveryPlan = discovery.makeDiscoveryPlan();
        try {
            for (NetworkEndpoint node : discoveryPlan.needDiscovery) {
                String msgCorrelationId = format("%s-%s:%s", correlationId,
                        node.getSwitchDpId(), node.getPortId());
                sendDiscoveryMessage(tuple, node, msgCorrelationId);
            }

            for (NetworkEndpoint node : discoveryPlan.discoveryFailure) {
                String msgCorrelationId = format("%s-%s:%s-fail", correlationId,
                        node.getSwitchDpId(), node.getPortId());
                // this is somewhat incongruous - we send failure to TE, but we send
                // discovery to FL ..
                // Reality is that the handleDiscovery/handleFailure below does the work
                //
                sendDiscoveryFailed(node.getSwitchDpId(), node.getPortId(), tuple, msgCorrelationId);
            }
        } catch (IOException e) {
            logger.error("Unable to encode message: {}", e);
        }
    }

    /**
     * Helper method for sending an ISL Discovery Message.
     */
    private void sendDiscoveryMessage(Tuple tuple, NetworkEndpoint node, String correlationId) throws IOException {
        DiscoverIslCommandData data = new DiscoverIslCommandData(node.getDatapath(), node.getPortNumber());
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(),
                correlationId, Destination.CONTROLLER);
        logger.debug("LINK: Send ISL discovery command: {}", message);
        collector.emit(SPEAKER_DISCO_STREAM, tuple, new Values(PAYLOAD, Utils.MAPPER.writeValueAsString(message)));
    }

    @Override
    protected void doWork(Tuple tuple) {
        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }
        //
        //        (crimi) - commenting out the filter code until we re-evaluate the design. Also, this code
        //        should probably be embedded in "handleIslEvent"
        //        /*
        //         * Check whether ISL Filter needs to be engaged.
        //         */
        //        String source = tuple.getSourceComponent();
        //        if (source.equals(OfEventWfmTopology.SPOUT_ID_INPUT)) {
        //            PopulateIslFilterAction action = new PopulateIslFilterAction(this, tuple, islFilter);
        //            action.run();
        //            return;
        //        }

        String json = tuple.getString(0);

        BaseMessage message;
        try {
            message = MAPPER.readValue(json, BaseMessage.class);
        } catch (IOException e) {
            collector.ack(tuple);
            logger.error("Unknown Message type={}", json);
            return;
        }

        try {
            if (message instanceof InfoMessage) {
                preHandleMessage((InfoMessage) message);
                dispatch(tuple, (InfoMessage) message);
            } else if (message instanceof HeartBeat) {
                logger.debug("Got speaker's heart beat");
                stateTransition(State.NEED_SYNC, State.OFFLINE);
            }
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            collector.ack(tuple);
        }
    }

    private void preHandleMessage(InfoMessage message) {
        InfoData data = message.getData();
        if (data instanceof DeactivateIslInfoData) {
            DeactivateIslInfoData deactivateIslInfoData = (DeactivateIslInfoData) data;
            discovery.handleFailed(deactivateIslInfoData.getSrcSwitchId(), deactivateIslInfoData.getSrcPort());
        }
    }

    private Switch cleanUpLogicalPorts(Switch originalSwitch) {
        if (originalSwitch == null) {
            return null;
        }
        return originalSwitch.toBuilder()
                .ports(originalSwitch.getPorts()
                        .stream()
                        .filter(port -> port.getNumber() <= bfdPortOffset).collect(Collectors.toList()))
                .build();
    }

    @VisibleForTesting
    protected void dispatch(Tuple tuple, InfoMessage infoMessage) {
        InfoData data = infoMessage.getData();
        if (data instanceof UnmanagedSwitchNotification) {
            UnmanagedSwitchNotification notification = (UnmanagedSwitchNotification) data;
            unmanagedSwitches.add(notification.getSwitchId());
        } else if (data instanceof NetworkDumpSwitchData) {
            NetworkDumpSwitchData networkDumpSwitchData = (NetworkDumpSwitchData) data;
            Switch switchWithNoBfdPorts = cleanUpLogicalPorts(networkDumpSwitchData.getSwitchRecord());
            unmanagedSwitches.remove(switchWithNoBfdPorts.getDatapath());
            logger.info("Event/WFM Sync: switch {}", data);
            discovery.registerSwitch(switchWithNoBfdPorts);
        } else if (data instanceof SwitchInfoData) {
            SwitchInfoData switchData = (SwitchInfoData) infoMessage.getData();
            unmanagedSwitches.remove(switchData.getSwitchId());
            Switch switchWithNoBfdPorts = cleanUpLogicalPorts(switchData.getSwitchRecord());
            SwitchInfoData switchDataWithNoBfdPorts = switchData.toBuilder().switchRecord(switchWithNoBfdPorts).build();
            InfoMessage cleanedInfoMessage = infoMessage.toBuilder().data(switchDataWithNoBfdPorts).build();
            handleSwitchEvent(tuple, cleanedInfoMessage);
            passToNetworkTopologyBolt(tuple, infoMessage);
        } else if (data instanceof PortInfoData) {
            PortInfoData portInfoData = (PortInfoData) data;
            int portNo = portInfoData.getPortNo();
            if (portNo > bfdPortOffset) {
                PortChangeType state = portInfoData.getState();
                if (state != PortChangeType.UP && state != PortChangeType.DOWN) {
                    return;
                }
                ((PortInfoData) data).setPortNo(portNo - bfdPortOffset);
            }
            unmanagedSwitches.remove(portInfoData.getSwitchId());
            handlePortEvent(tuple, (PortInfoData) data);
            passToNetworkTopologyBolt(tuple, infoMessage);
        } else if (data instanceof IslInfoData) {
            IslInfoData islInfoData = (IslInfoData) data;
            unmanagedSwitches.remove(islInfoData.getSource().getSwitchId());
            handleIslEvent(tuple, infoMessage);
        } else if (data instanceof DiscoPacketSendingConfirmation) {
            DiscoPacketSendingConfirmation confirmation = (DiscoPacketSendingConfirmation) data;
            unmanagedSwitches.remove(confirmation.getEndpoint().getDatapath());
            handleSentDiscoPacket(confirmation);
        } else if (data instanceof DeactivateIslInfoData) {
            DeactivateIslInfoData deactivateIslInfoData = (DeactivateIslInfoData) data;
            unmanagedSwitches.remove(((DeactivateIslInfoData) data).getSrcSwitchId());
            discovery.handleFailed(deactivateIslInfoData.getSrcSwitchId(), deactivateIslInfoData.getSrcPort());
        } else {
            reportInvalidEvent(data);
        }
    }

    private void stateTransition(State switchTo) {
        logger.info("State transition to {} (current {})", switchTo, state);
        state = switchTo;
    }

    private void stateTransition(State switchTo, State onlyInState) {
        if (state == onlyInState) {
            stateTransition(switchTo);
        }
    }

    private void reportInvalidEvent(InfoData event) {
        logger.error(
                "Unhandled event: state={}, type={}", state,
                event.getClass().getName());
    }

    protected void handleSwitchEvent(Tuple tuple, InfoMessage infoMessage) {
        SwitchInfoData switchData = (SwitchInfoData) infoMessage.getData();
        SwitchId switchId = switchData.getSwitchId();
        SwitchChangeType switchState = switchData.getState();

        String logMessage = format("DISCO: Switch Event: switch=%s state=%s", switchId, switchState);
        logWrapper.onSwitchDiscovery(Level.INFO, logMessage, switchId, switchState);

        if (switchState == SwitchChangeType.DEACTIVATED) {
            passToNetworkTopologyBolt(tuple, infoMessage);
        } else if (switchState == SwitchChangeType.ACTIVATED) {
            // It's possible that we get duplicated switch up events .. particulary if
            // FL goes down and then comes back up; it'll rebuild its switch / port information.
            // NB: need to account for this, and send along to TE to be conservative.
            discovery.registerSwitch(switchData.getSwitchRecord());

            // Produce port UP log records to match with current behavior i.e. switch-ADD event is a predecessor
            // for set of port-UP events.
            for (SwitchPort port : switchData.getSwitchRecord().getPorts()) {
                if (SwitchPort.State.UP == port.getState()) {
                    logger.info("DISCO: Port Event: switch={} port={} state={}",
                                switchId, port.getNumber(), PortChangeType.UP);
                }
            }
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("SWITCH Event: ignoring state: {}", switchState);
        }
    }

    private void passToNetworkTopologyBolt(Tuple tuple, InfoMessage message) {
        collector.emit(NETWORK_TOPOLOGY_CHANGE_STREAM, tuple, new Values(message));
    }

    private void handlePortEvent(Tuple tuple, PortInfoData portData) {
        final SwitchId switchId = portData.getSwitchId();
        final int portId = portData.getPortNo();
        String updown = portData.getState().toString();
        String logMessage = format("DISCO: Port Event: switch=%s port=%d state=%s", switchId, portId, updown);
        logWrapper.onPortDiscovery(Level.INFO, logMessage, switchId, portId, updown);

        if (isPortUpOrCached(updown)) {
            discovery.handlePortUp(switchId, portId);
        } else if (updown.equals(OfeMessageUtils.PORT_DOWN)) {
            discovery.handlePortDown(switchId, portId);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("PORT Event: ignoring state: {}", updown);
        }
    }

    private void handleIslEvent(Tuple tuple, InfoMessage infoMessage) {
        IslInfoData discoveredIsl = (IslInfoData) infoMessage.getData();
        String correlationId = infoMessage.getCorrelationId();

        PathNode srcNode = discoveredIsl.getSource();
        final SwitchId srcSwitch = srcNode.getSwitchId();
        final int srcPort = srcNode.getPortNo();

        PathNode dstNode = discoveredIsl.getDestination();
        final SwitchId dstSwitch = dstNode.getSwitchId();
        final int dstPort = dstNode.getPortNo();

        IslChangeType islState = discoveredIsl.getState();
        boolean stateChanged = false;

        /*
         * TODO: would be good to merge more of this behavior / business logic within DiscoveryManager
         *  The reason is so that we consolidate behavior related to Network Topology Discovery into
         *  one place.
         */
        if (IslChangeType.DISCOVERED.equals(islState)) {
            if (discoveredIsl.isSelfLooped()) {
                String logMessage = format("DISCO: ISL Event: loop detected: switch=%s srcPort=%d dstPort=%d",
                        srcSwitch, srcPort, dstPort);
                logWrapper.onIslDiscoveryLoop(Level.INFO, logMessage, srcSwitch, srcPort, dstPort, islState);
                return;
            }
            if (discovery.isIslMoved(srcSwitch, srcPort, dstSwitch, dstPort)) {
                handleMovedIsl(tuple, srcSwitch, srcPort, dstSwitch, dstPort, correlationId);
            }
            stateChanged = discovery.handleDiscovered(srcSwitch, srcPort, dstSwitch, dstPort);
            // If the state has changed, and since we've discovered one end of an ISL, let's make
            // sure we can test the other side as well.
            if (stateChanged && !discovery.isInDiscoveryPlan(dstSwitch, dstPort)) {
                discovery.handlePortUp(dstSwitch, dstPort);
            }
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("ISL Event: ignoring state: {}", islState);
        }

        if (stateChanged) {
            // If the state changed, notify the TE.
            String logMessage = format("DISCO: ISL Event: switch=%s port=%d state=%s", srcSwitch, srcPort, islState);
            logWrapper.onIslDiscovery(Level.INFO, logMessage, srcSwitch, srcPort, dstPort, islState);
            passToNetworkTopologyBolt(tuple, infoMessage);
        }
    }

    // TODO: Who are some of the recipients of IslFail message? ie who are we emitting this to?
    //      - From a code search, we see these code bases refering to IslInfoData:
    //          - wfm/topology/cache
    //          - wfm/topology/islstats
    //          - simulator/bolts/SpeakerBolt
    //          - services/topology-engine/queue-engine/topologylistener/eventhandler.py
    //          - services/src/topology .. service/impl/IslServiceImpl .. service/IslService
    //          - services/src/topology .. messaging/kafka/KafkaMessageConsumer
    //          - services/src/pce .. NetworkCache .. FlowCache ..
    private void sendDiscoveryFailed(SwitchId switchId, int portId, Tuple tuple, String correlationId) {
        PathNode node = new PathNode(switchId, portId, 0, 0L);
        InfoData data = new IslInfoData(0L, node, null, 0L, IslChangeType.FAILED, 0L, false);
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), correlationId);

        if (unmanagedSwitches.contains(switchId)) {
            logger.warn("LINK: Ignoring ISL discovery failure since Floodlight is unavailable={}", message);
            return;
        }
        passToNetworkTopologyBolt(tuple, message);
        discovery.handleFailed(switchId, portId);
        logger.warn("LINK: Send ISL discovery failure message={}", message);
    }

    private boolean isPortUpOrCached(String state) {
        return OfeMessageUtils.PORT_UP.equals(state) || OfeMessageUtils.PORT_ADD.equals(state)
                || PortChangeType.CACHED.getType().equals(state);
    }

    private void handleMovedIsl(Tuple tuple, SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort,
                                String correlationId) {
        NetworkEndpoint dstEndpoint = discovery.getLinkDestination(srcSwitch, srcPort);
        logger.info("Link is moved from {}_{} - {}_{} to endpoint {}_{}", srcSwitch, srcPort,
                dstEndpoint.getSwitchDpId(), dstEndpoint.getPortId(), dstSwitch, dstPort);
        // deactivate reverse link
        discovery.deactivateLinkFromEndpoint(dstEndpoint);

        PathNode srcNode = new PathNode(srcSwitch, srcPort, 0);
        PathNode dstNode = new PathNode(dstEndpoint.getSwitchDpId(), dstEndpoint.getPortId(), 1);
        IslInfoData infoData = new IslInfoData(srcNode, dstNode, IslChangeType.MOVED, false);
        InfoMessage message = new InfoMessage(infoData, System.currentTimeMillis(), correlationId);
        passToNetworkTopologyBolt(tuple, message);

        // we should send reverse link as well to modify status in TE
        srcNode = new PathNode(dstEndpoint.getSwitchDpId(), dstEndpoint.getPortId(), 0);
        dstNode = new PathNode(srcSwitch, srcPort, 1);
        IslInfoData reverseLink = new IslInfoData(srcNode, dstNode, IslChangeType.MOVED, false);
        message = new InfoMessage(reverseLink, System.currentTimeMillis(), correlationId);
        passToNetworkTopologyBolt(tuple, message);
    }

    private void handleSentDiscoPacket(DiscoPacketSendingConfirmation confirmation) {
        logger.debug("Discovery packet is sent from {}", confirmation);
        discovery.handleSentDiscoPacket(confirmation.getEndpoint());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        declarer.declareStream(SPEAKER_STREAM, fields);
        declarer.declareStream(SPEAKER_DISCO_STREAM, fields);
        declarer.declareStream(NETWORK_TOPOLOGY_CHANGE_STREAM, new Fields(PAYLOAD));
        // FIXME(dbogun): use proper tuple format
        declarer.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    @Override
    public AbstractDumpState dumpState() {
        Set<DiscoveryLink> links = linksBySwitch.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        return new OFELinkBoltState(links, islFilter.getMatchSet());
    }

    @Override
    public String getCtrlStreamId() {
        return STREAM_ID_CTRL;
    }

    @Override
    public void clearState() {
        logger.info("ClearState request has been received.");
        initState(new InMemoryKeyValueState<>());
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(SwitchId switchId) {

        Set<DiscoveryLink> filteredDiscoveryList = linksBySwitch.get(switchId);

        Set<DiscoveryLink> filterdIslFilter = islFilter.getMatchSet().stream()
                .filter(node -> node.getSource().getSwitchDpId().equals(switchId))
                .collect(Collectors.toSet());


        return new OFELinkBoltState(filteredDiscoveryList, filterdIslFilter);
    }

    @Override
    public TopologyContext getContext() {
        return context;
    }

    @Override
    public OutputCollector getOutput() {
        return collector;
    }

    @VisibleForTesting
    enum State {
        NEED_SYNC,
        WAIT_SYNC,
        SYNC_IN_PROGRESS,
        OFFLINE,
        MAIN
    }
}
