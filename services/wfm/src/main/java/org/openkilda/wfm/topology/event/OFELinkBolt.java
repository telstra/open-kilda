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

package org.openkilda.wfm.topology.event;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.Utils.PAYLOAD;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.storm.kafka.spout.internal.Timer;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.HeartBeat;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkSyncBeginMarker;
import org.openkilda.messaging.info.discovery.NetworkSyncEndMarker;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.wfm.OFEMessageUtils;
import org.openkilda.wfm.WatchDog;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.isl.DiscoveryManager;
import org.openkilda.wfm.isl.DummyIIslFilter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.TopologyConfig;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class is the main class for tracking network topology. The most complicated part of
 * the network topology is links and generating health checks.
 *
 * Because this class / topology acts as a regulator / throttler for what the topology engine
 * receives, it is responsible for passing along changes in the network to the topology engine.
 * In other words, this class acts as a buffer for what the topology engine receives. As such, it
 * needs to send Switch / Port / Link up/down status messages.
 *
 * Regarding Storm's KeyValueState .. it
 * doesn't have a keys() feature .. so there is at this stage only one object in it, which holds hashmaps, etc.
 *
 * Cache warming:
 * For update code in Storm, we need to kill and load the new topology, and bolt loses all
 * internal state. For restore data in bolt we send a message to FL and wait till callback message
 * with network data arrive. We don't process common messages and mark it as fail before that.
 * UML Diagram is here https://github.com/telstra/open-kilda/issues/213
 */
public class OFELinkBolt
        extends AbstractTickStatefulBolt<KeyValueState<String, Object>>
        implements ICtrlBolt {
    private static final Logger logger = LoggerFactory.getLogger(OFELinkBolt.class);
    private static final int BOLT_TICK_INTERVAL = 1;

    private final String STREAM_ID_CTRL = "ctrl";
    private final String STATE_ID_DISCOVERY = "discovery-manager";
    private final String topoEngTopic;
    private final String islDiscoveryTopic;

    private final int islHealthCheckInterval;
    private final int islHealthCheckTimeout;
    private final int islHealthFailureLimit;
    private final float watchDogInterval;
    private WatchDog watchDog;
    private TopologyContext context;
    private OutputCollector collector;

    private DummyIIslFilter islFilter;
    private DiscoveryManager discovery;
    private LinkedList<DiscoveryLink> discoveryQueue;

    private String dumpRequestCorrelationId = null;
    private float dumpRequestTimeout;
    private Timer dumpRequestTimer;
    private State state = State.NEED_SYNC;

    /**
     * Default constructor .. default health check frequency
     */
    public OFELinkBolt(TopologyConfig config) {
        super(BOLT_TICK_INTERVAL);

        this.islHealthCheckInterval = config.getDiscoveryInterval();
        this.islHealthCheckTimeout = config.getDiscoveryTimeout();
        this.islHealthFailureLimit = config.getDiscoveryLimit();

        watchDogInterval = config.getDiscoverySpeakerFailureTimeout();
        dumpRequestTimeout = config.getDiscoveryDumpRequestTimeout();

        topoEngTopic = config.getKafkaTopoEngTopic();
        islDiscoveryTopic = config.getKafkaSpeakerTopic();
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
        watchDog = new WatchDog(watchDogInterval);

        // NB: First time the worker is created this will be null
        // TODO: what happens to state as workers go up or down
        Object payload = state.get(STATE_ID_DISCOVERY);
        if (payload == null) {
            payload = discoveryQueue = new LinkedList<>();
            state.put(islDiscoveryTopic, payload);
        } else {
            discoveryQueue = (LinkedList<DiscoveryLink>) payload;
        }

        discovery = new DiscoveryManager(
                islFilter, discoveryQueue, islHealthCheckInterval, islHealthCheckTimeout, islHealthFailureLimit
        );
    }

    /**
     * Send ISL health checks for all links
     */
    @Override
    protected void doTick(Tuple tuple) {
        boolean isSpeakerAvailable = watchDog.isAvailable();

        if (!isSpeakerAvailable) {
            stateTransition(State.OFFLINE);
        }

        switch (state) {
            case NEED_SYNC:
                dumpRequestCorrelationId = sendNetworkRequest(tuple);
                enableDumpRequestTimer();
                stateTransition(State.WAIT_SYNC);
                break;

            case WAIT_SYNC:
            case SYNC_IN_PROGRESS:
                if (dumpRequestTimer.isExpiredResetOnTrue()) {
                    logger.error("Did not get network dump, send one more dump request");
                    dumpRequestCorrelationId = sendNetworkRequest(tuple);
                }
                break;

            case OFFLINE:
                if (isSpeakerAvailable) {
                    logger.info("Switch into ONLINE mode");
                    stateTransition(State.NEED_SYNC);
                }
                break;

            case MAIN:
                DiscoveryManager.Plan discoveryPlan = discovery.makeDiscoveryPlan();
                try {
                    for (NetworkEndpoint node : discoveryPlan.needDiscovery) {
                        sendDiscoveryMessage(tuple, node);
                    }

                    for (NetworkEndpoint node : discoveryPlan.discoveryFailure) {
                        // this is somewhat incongruous - we send failure to TE, but we send
                        // discovery to FL ..
                        // Reality is that the handleDiscovery/handleFailure below does the work
                        //
                        sendDiscoveryFailed(node.getSwitchDpId(), node.getPortId(), tuple);
                    }
                } catch (IOException e) {
                    logger.error("Unable to encode message: {}", e);
                }
                break;

        }
    }

    /**
     * Send network dump request to FL
     */
    private String sendNetworkRequest(Tuple tuple) {
        String correlationId = UUID.randomUUID().toString();
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER);

        logger.info(
                "Send network dump request (correlation-id: {})",
                correlationId);

        try {
            String json = Utils.MAPPER.writeValueAsString(command);
            collector.emit(islDiscoveryTopic, tuple, new Values(PAYLOAD, json));
        }
        catch (JsonProcessingException exception)
        {
            logger.error("Could not serialize network cache request", exception);
        }

        return correlationId;
    }

    /**
     * Helper method for sending an ISL Discovery Message
     */
    private void sendDiscoveryMessage(Tuple tuple, NetworkEndpoint node) throws IOException {
        String json = OFEMessageUtils.createIslDiscovery(node.getSwitchDpId(), node.getPortId());
        logger.debug("LINK: Send ISL discovery command: {}", json);
        collector.emit(islDiscoveryTopic, tuple, new Values(PAYLOAD, json));
    }

    @Override
    protected void doWork(Tuple tuple) {
        if (CtrlAction.boltHandlerEntrance(this, tuple))
            return;
//
// (crimi) - commenting out the filter code until we re-evaluate the design. Also, this code
// should probably be embedded in "handleIslEvent"
//        /*
//         * Check whether ISL Filter needs to be engaged.
//         */
//        String source = tuple.getSourceComponent();
//        if (source.equals(OFEventWFMTopology.SPOUT_ID_INPUT)) {
//            PopulateIslFilterAction action = new PopulateIslFilterAction(this, tuple, islFilter);
//            action.run();
//            return;
//        }

        String json = tuple.getString(0);

        BaseMessage message;
        try {
            message = MAPPER.readValue(json, BaseMessage.class);
            watchDog.reset();
        } catch (IOException e) {
            collector.ack(tuple);
            logger.error("Unknown Message type={}", json);
            return;
        }

        try {
            if (message instanceof InfoMessage) {
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

    private void dispatch(Tuple tuple, InfoMessage infoMessage) {
        switch (state) {
            case NEED_SYNC:
                dispatchNeedSync(tuple, infoMessage);
                break;
            case WAIT_SYNC:
                dispatchWaitSync(tuple, infoMessage);
                break;
            case SYNC_IN_PROGRESS:
                dispatchSyncInProgress(tuple, infoMessage);
                break;
            case OFFLINE:
                dispatchOffline(tuple, infoMessage);
                break;
            case MAIN:
                dispatchMain(tuple, infoMessage);
                break;
            default:
                reportInvalidEvent(infoMessage.getData());
        }
    }

    private void dispatchNeedSync(Tuple tuple, InfoMessage infoMessage) {
        logger.warn("Bolt internal state is out of sync with FL, skip tuple");
    }

    private void dispatchWaitSync(Tuple tuple, InfoMessage infoMessage) {
        InfoData data = infoMessage.getData();
        if (data instanceof NetworkSyncBeginMarker) {
            if (dumpRequestCorrelationId.equals(infoMessage.getCorrelationId())) {
                logger.info("Got response on network sync request, start processing network events");
                enableDumpRequestTimer();
                stateTransition(State.SYNC_IN_PROGRESS);
            } else {
                logger.warn(
                        "Got response on network sync request with invalid " +
                        "correlation-id(expect: \"{}\", got: \"{}\")",
                        dumpRequestCorrelationId, infoMessage.getCorrelationId());
            }
        } else {
            reportInvalidEvent(data);
        }
    }

    private void dispatchSyncInProgress(Tuple tuple, InfoMessage infoMessage) {
        InfoData data = infoMessage.getData();
        if (data instanceof SwitchInfoData) {
            handleSwitchEvent(tuple, (SwitchInfoData) data);
        } else if (data instanceof PortInfoData) {
            handlePortEvent(tuple, (PortInfoData) data);
        } else if (data instanceof NetworkSyncEndMarker) {
            logger.info("End of network sync stream received");
            stateTransition(State.MAIN);
        } else {
            reportInvalidEvent(data);
        }
    }

    private void dispatchOffline(Tuple tuple, InfoMessage infoMessage) {
        logger.warn("Got input while in offline mode, it mean the possibility to try sync state");
        watchDog.reset();
        stateTransition(State.NEED_SYNC);
    }

    private void dispatchMain(Tuple tuple, InfoMessage infoMessage) {
        InfoData data = infoMessage.getData();
        if (data instanceof SwitchInfoData) {
            handleSwitchEvent(tuple, (SwitchInfoData) data);
            passToTopologyEngine(tuple);
        } else if (data instanceof PortInfoData) {
            handlePortEvent(tuple, (PortInfoData) data);
            passToTopologyEngine(tuple);
        } else if (data instanceof IslInfoData) {
            handleIslEvent(tuple, (IslInfoData) data);
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

    private void handleSwitchEvent(Tuple tuple, SwitchInfoData switchData) {
        String switchID = switchData.getSwitchId();
        String state = switchData.getState().toString();
        logger.info("DISCO: Switch Event: switch={} state={}", switchID, state);

        if (SwitchState.DEACTIVATED.getType().equals(state)) {
            // current logic: switch down means stop checking associated ports/links.
            // - possible extra steps of validation of switch down should occur elsewhere
            // - possible extra steps of generating link down messages aren't important since
            //      the TPE will drop the switch node from its graph.
            discovery.handleSwitchDown(switchID);
        } else if (SwitchState.ACTIVATED.getType().equals(state)) {
            // It's possible that we get duplicated switch up events .. particulary if
            // FL goes down and then comes back up; it'll rebuild its switch / port information.
            // NB: need to account for this, and send along to TE to be conservative.
            discovery.handleSwitchUp(switchID);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("SWITCH Event: ignoring state: {}", state);
        }
    }

    /**
     * Pass the original message along, to the Topology Engine topic.
     */
    private void passToTopologyEngine(Tuple tuple) {
        String json = tuple.getString(0);
        collector.emit(topoEngTopic, tuple, new Values(PAYLOAD, json));
    }

    private void passToTopologyEngine(Tuple tuple, InfoMessage message) {
        try {
            String json = Utils.MAPPER.writeValueAsString(message);
            collector.emit(topoEngTopic, tuple, new Values(PAYLOAD, json));
        } catch (JsonProcessingException e) {
            logger.error("Error during json processing", e);
        }
    }

    private void handlePortEvent(Tuple tuple, PortInfoData portData) {
        final String switchId = portData.getSwitchId();
        final int portId = portData.getPortNo();
        String updown = portData.getState().toString();
        logger.info("DISCO: Port Event: switch={} port={} state={}", switchId, portId, updown);

        if (isPortUpOrCached(updown)) {
            discovery.handlePortUp(switchId, portId);
        } else if (updown.equals(OFEMessageUtils.PORT_DOWN)) {
            discovery.handlePortDown(switchId, portId);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("PORT Event: ignoring state: {}", updown);
        }
    }

    private void handleIslEvent(Tuple tuple, IslInfoData discoveredIsl) {
        PathNode srcNode = discoveredIsl.getPath().get(0);
        final String srcSwitch = srcNode.getSwitchId();
        final int srcPort = srcNode.getPortNo();

        PathNode dstNode = discoveredIsl.getPath().get(1);
        final String dstSwitch = dstNode.getSwitchId();
        final int dstPort = dstNode.getPortNo();

        IslChangeType state = discoveredIsl.getState();
        boolean stateChanged = false;

        /*
         * TODO: would be good to merge more of this behavior / business logic within DiscoveryManager
         *  The reason is so that we consolidate behavior related to Network Topology Discovery into
         *  one place.
         */
        if (IslChangeType.DISCOVERED.equals(state)) {
            if (discovery.isIslMoved(srcSwitch, srcPort, dstSwitch, dstPort)) {
                handleMovedIsl(tuple, srcSwitch, srcPort, dstSwitch, dstPort);
            }
            stateChanged = discovery.handleDiscovered(srcSwitch, srcPort, dstSwitch, dstPort);
            // If the state has changed, and since we've discovered one end of an ISL, let's make
            // sure we can test the other side as well.
            if (stateChanged && discoveredIsl.getPath().size() > 1) {
                if (!discovery.checkForIsl(dstSwitch, dstPort)) {
                    // Only call PortUp if we aren't checking for ISL. Otherwise, we could end up in an
                    // infinite cycle of always sending a Port UP when one side is discovered.
                    discovery.handlePortUp(dstSwitch, dstPort);
                }

            }

        } else if (IslChangeType.FAILED.equals(state)) {
            stateChanged = discovery.handleFailed(srcSwitch, srcPort);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("ISL Event: ignoring state: {}", state);
        }

        if (stateChanged) {
            // If the state changed, notify the TE.
            logger.info("DISCO: ISL Event: switch={} port={} state={}", srcSwitch, srcPort, state);
            passToTopologyEngine(tuple);
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
    private void sendDiscoveryFailed(String switchId, int portId, Tuple tuple) throws IOException {
        String discoFail = OFEMessageUtils.createIslFail(switchId, portId);
//        Values dataVal = new Values(PAYLOAD, discoFail, switchId, portId, OFEMessageUtils.LINK_DOWN);
//        collector.emit(topoEngTopic, tuple, dataVal);
        collector.emit(topoEngTopic, tuple, new Values(PAYLOAD, discoFail));
        discovery.handleFailed(switchId, portId);
        logger.warn("LINK: Send ISL discovery failure message={}", discoFail);
    }

    private boolean isPortUpOrCached(String state) {
        return OFEMessageUtils.PORT_UP.equals(state) || OFEMessageUtils.PORT_ADD.equals(state) ||
                PortChangeType.CACHED.getType().equals(state);
    }

    private void enableDumpRequestTimer() {
        long expireDelay = (int) (dumpRequestTimeout * 1000);
        dumpRequestTimer = new Timer(expireDelay, expireDelay, TimeUnit.MILLISECONDS);
    }

    private void handleMovedIsl(Tuple tuple, String srcSwitch, int srcPort, String dstSwitch, int dstPort) {
        NetworkEndpoint dstEndpoint = discovery.getLinkDestination(srcSwitch, srcPort);
        logger.info("Link is moved from {}_{} - {}_{} to endpoint {}_{}", srcSwitch, srcPort,
                dstEndpoint.getSwitchDpId(), dstEndpoint.getPortId(), dstSwitch, dstPort);

        PathNode srcNode = new PathNode(srcSwitch, srcPort, 0);
        PathNode dstNode = new PathNode(dstEndpoint.getSwitchDpId(), dstEndpoint.getPortId(), 1);
        IslInfoData infoData = new IslInfoData(Lists.newArrayList(srcNode, dstNode), IslChangeType.MOVED);
        InfoMessage message = new InfoMessage(infoData, System.currentTimeMillis(), UUID.randomUUID().toString());
        passToTopologyEngine(tuple, message);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(islDiscoveryTopic, new Fields("key", "message"));
        declarer.declareStream(topoEngTopic, new Fields("key", "message"));
        // FIXME(dbogun): use proper tuple format
        declarer.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    @Override
    public AbstractDumpState dumpState() {
        return new OFELinkBoltState(discoveryQueue, islFilter.getMatchSet());
    }

    @Override
    public String getCtrlStreamId() {
        return STREAM_ID_CTRL;
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(String switchId) {

        List<DiscoveryLink> filteredDiscoveryQueue =  discoveryQueue.stream().
                filter(node -> node.getSrcEndpoint().getSwitchDpId().equals(switchId)).
                collect(Collectors.toList());

        Set<DiscoveryLink> filterdIslFilter = islFilter.getMatchSet().stream().
                filter(node -> node.getSrcEndpoint().getSwitchDpId().equals(switchId)).
                collect(Collectors.toSet());


        return new OFELinkBoltState(filteredDiscoveryQueue, filterdIslFilter);
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
    List<DiscoveryLink> getDiscoveryQueue()
    {
        return this.discoveryQueue;
    }

    private enum State {
        NEED_SYNC,
        WAIT_SYNC,
        SYNC_IN_PROGRESS,
        OFFLINE,
        MAIN
    }
}
