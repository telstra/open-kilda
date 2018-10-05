/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.event.bolt;

import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.OfeMessageUtils;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.isl.DiscoveryManager;
import org.openkilda.wfm.isl.DummyIIslFilter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.event.OFEventWfmTopologyConfig;
import org.openkilda.wfm.topology.event.OFEventWfmTopologyConfig.DiscoveryConfig;
import org.openkilda.wfm.topology.event.model.Sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
public class OfeLinkBolt extends AbstractBolt implements ICtrlBolt {
    private static final Logger logger = LoggerFactory.getLogger(OfeLinkBolt.class);

    public static final String BOLT_ID = ComponentId.ISL_DISCOVERY.toString();

    private static final String STREAM_ID_CTRL = "ctrl";

    public static final String SPEAKER_DISCO_STREAM = "speaker.disco";
    public static final String NETWORK_TOPOLOGY_CHANGE_STREAM = "network-topology-change";

    private final int islHealthCheckInterval;
    private final int islHealthCheckTimeout;
    private final int islHealthFailureLimit;
    private final int islKeepRemovedTimeout;
    private TopologyContext context;

    private DummyIIslFilter islFilter;
    private DiscoveryManager discovery;
    private Map<SwitchId, Set<DiscoveryLink>> linksBySwitch;

    /**
     * Default constructor .. default health check frequency
     */
    public OfeLinkBolt(OFEventWfmTopologyConfig config) {
        DiscoveryConfig discoveryConfig = config.getDiscoveryConfig();
        islHealthCheckInterval = discoveryConfig.getDiscoveryInterval();
        Preconditions.checkArgument(islHealthCheckInterval > 0,
                "Invalid value for DiscoveryInterval: %s", islHealthCheckInterval);
        islHealthCheckTimeout = discoveryConfig.getDiscoveryTimeout();
        Preconditions.checkArgument(islHealthCheckTimeout > 0,
                "Invalid value for DiscoveryTimeout: %s", islHealthCheckTimeout);
        islHealthFailureLimit = discoveryConfig.getDiscoveryLimit();
        islKeepRemovedTimeout = discoveryConfig.getKeepRemovedIslTimeout();
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        if (CtrlAction.boltHandlerEntrance(this, input)) {
            return;
        }

        // FIXME(surabujin): the filter code was never used and on this moment must be extremely outdated
        // (crimi) - commenting out the filter code until we re-evaluate the design. Also, this code
        // should probably be embedded in "handleIslEvent"
        // /*
        //  * Check whether ISL Filter needs to be engaged.
        //  */
        // String source = tuple.getSourceComponent();
        // if (source.equals(OfEventWfmTopology.SPOUT_ID_INPUT)) {
        //     PopulateIslFilterAction action = new PopulateIslFilterAction(this, tuple, islFilter);
        //     action.run();
        //     return;
        // }

        String source = input.getSourceComponent();
        String stream = input.getSourceStreamId();
        if (MonotonicTick.BOLT_ID.equals(source)) {
            doTick(input);
        } else if (FlMonitor.STREAM_SYNC_ID.equals(stream)) {
            consumeSync(input);
        } else {
            dispatchMain(input);
        }
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);

        this.islFilter = new DummyIIslFilter();
        this.context = context;

        setupDiscoveryManager(new HashMap<>());
    }

    @VisibleForTesting
    void resetDiscoveryManager(Map<SwitchId, Set<DiscoveryLink>> links) {
        setupDiscoveryManager(links);
    }

    private void setupDiscoveryManager(Map<SwitchId, Set<DiscoveryLink>> links) {
        // DiscoveryManager counts failures as failed attempts,
        // so we need to convert islHealthCheckTimeout (which is in ticks) into attempts.
        int islConsecutiveFailureLimit = (int) Math.ceil(islHealthCheckTimeout / (float) islHealthCheckInterval);

        discovery = new DiscoveryManager(links, islHealthCheckInterval, islConsecutiveFailureLimit,
                islHealthFailureLimit, islKeepRemovedTimeout);
        linksBySwitch = links;
    }

    /**
     * Send ISL health checks for all links.
     */
    private void doTick(Tuple tuple) throws PipelineException {
        DiscoveryManager.Plan discoveryPlan = discovery.makeDiscoveryPlan();
        for (NetworkEndpoint node : discoveryPlan.needDiscovery) {
            sendDiscoveryMessage(tuple, node);
        }

        for (NetworkEndpoint node : discoveryPlan.discoveryFailure) {
            discovery.handleFailed(node);
            sendDiscoveryFailed(node, tuple);
        }
    }

    /**
     * Helper method for sending an ISL Discovery Message.
     */
    private void sendDiscoveryMessage(Tuple tuple, NetworkEndpoint node)
            throws PipelineException {
        logger.debug("LINK: Send ISL discovery command: {}", node);
        DiscoverIslCommandData payload = new DiscoverIslCommandData(node.getDatapath(), node.getPortNumber());
        CommandContext nestedContext = pullContext(tuple).makeNested(
                String.format("%s-%s", node.getDatapath(), node.getPortNumber()));
        getOutput().emit(SPEAKER_DISCO_STREAM, tuple, new Values(payload, nestedContext));
    }

    private void consumeSync(Tuple tuple) {
        logger.info("Apply FL sync data");

        Sync sync = (Sync) tuple.getValueByField(FlMonitor.FIELD_ID_SYNC);
        for (Entry<SwitchId, Set<Integer>> entry : sync.getActivePorts().entrySet()) {
            for (Integer port : entry.getValue()) {
                logger.info("Sync - get active port - {}-{}", entry.getKey(), port);
                discovery.registerPort(entry.getKey(), port);
            }
        }
    }

    private void dispatchMain(Tuple tuple) throws PipelineException {
        Message message = (Message) tuple.getValueByField(FlMonitor.FIELD_ID_INPUT);

        if (message instanceof InfoMessage) {
            dispatchNetworkEvent(tuple, (InfoMessage) message);
        }
    }

    private void dispatchNetworkEvent(Tuple tuple, InfoMessage infoMessage) throws PipelineException {
        InfoData data = infoMessage.getData();
        if (data instanceof SwitchInfoData) {
            handleSwitchEvent(tuple, (SwitchInfoData) data);
            passToNetworkTopologyBolt(tuple, infoMessage);
        } else if (data instanceof PortInfoData) {
            handlePortEvent(tuple, (PortInfoData) data);
            passToNetworkTopologyBolt(tuple, infoMessage);
        } else if (data instanceof IslInfoData) {
            handleIslEvent(tuple, infoMessage);
        } else if (data instanceof DiscoPacketSendingConfirmation) {
            handleSentDiscoPacket((DiscoPacketSendingConfirmation) data);
        } else {
            reportInvalidEvent(data);
        }
    }

    private void reportInvalidEvent(InfoData event) {
        logger.error("Unhandled event: type={}", event.getClass().getName());
    }

    private void handleSwitchEvent(Tuple tuple, SwitchInfoData switchData) throws PipelineException {
        SwitchId switchId = switchData.getSwitchId();
        SwitchChangeType switchState = switchData.getState();
        logger.info("DISCO: Switch Event: switch={} state={}", switchId, switchState);

        if (switchState == SwitchChangeType.ACTIVATED) {
            // It's possible that we get duplicated switch up events .. particulary if
            // FL goes down and then comes back up; it'll rebuild its switch / port information.
            // NB: need to account for this, and send along to TE to be conservative.
            discovery.handleSwitchUp(switchId);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("SWITCH Event: ignoring state: {}", switchState);
        }
    }

    private void handlePortEvent(Tuple tuple, PortInfoData portData) {
        final SwitchId switchId = portData.getSwitchId();
        final int portId = portData.getPortNo();
        String updown = portData.getState().toString();
        logger.info("DISCO: Port Event: switch={} port={} state={}", switchId, portId, updown);

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
                logger.info("DISCO: ISL Event: loop detected: switch={} srcPort={} dstPort={}",
                        srcSwitch, srcPort, dstPort);
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
            logger.info("DISCO: ISL Event: switch={} port={} state={}", srcSwitch, srcPort, islState);
            passToNetworkTopologyBolt(tuple, infoMessage);
        }
    }

    private void sendDiscoveryFailed(NetworkEndpoint endpoint, Tuple tuple) {
        String correlationId = String.format("%s-%s:%s-fail", UUID.randomUUID(),
                endpoint.getDatapath(), endpoint.getPortNumber());
        PathNode node = new PathNode(endpoint.getDatapath(), endpoint.getPortNumber(), 0, 0L);
        InfoData data = new IslInfoData(0L, node, null, 0L, IslChangeType.FAILED, 0L);
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), correlationId);

        passToNetworkTopologyBolt(tuple, message);
        discovery.handleFailed(endpoint);
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
        IslInfoData infoData = new IslInfoData(srcNode, dstNode, IslChangeType.MOVED);
        InfoMessage message = new InfoMessage(infoData, System.currentTimeMillis(), correlationId);
        passToNetworkTopologyBolt(tuple, message);

        // we should send reverse link as well to modify status in TE
        srcNode = new PathNode(dstEndpoint.getSwitchDpId(), dstEndpoint.getPortId(), 0);
        dstNode = new PathNode(srcSwitch, srcPort, 1);
        IslInfoData reverseLink = new IslInfoData(srcNode, dstNode, IslChangeType.MOVED);
        message = new InfoMessage(reverseLink, System.currentTimeMillis(), correlationId);
        passToNetworkTopologyBolt(tuple, message);
    }

    private void handleSentDiscoPacket(DiscoPacketSendingConfirmation confirmation) {
        logger.debug("Discovery packet is sent from {}", confirmation);
        discovery.handleSentDiscoPacket(confirmation.getEndpoint());
    }

    private void passToNetworkTopologyBolt(Tuple tuple, InfoMessage message) {
        getOutput().emit(NETWORK_TOPOLOGY_CHANGE_STREAM, tuple, new Values(message));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(SPEAKER_DISCO_STREAM, new Fields(SpeakerEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
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
        return super.getOutput();
    }
}
