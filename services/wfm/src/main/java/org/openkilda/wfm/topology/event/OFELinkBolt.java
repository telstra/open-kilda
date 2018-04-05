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
import org.openkilda.messaging.info.discovery.NetworkSyncMarker;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.DiscoveryNode;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private boolean isOnline = true;
    private TopologyContext context;
    private OutputCollector collector;

    private DummyIIslFilter islFilter;
    private DiscoveryManager discovery;
    private LinkedList<DiscoveryNode> discoveryQueue;

    /**
     * Initialization flag
     */
    private boolean isReceivedCacheInfo = false;

    /**
     * Cache request send flag
     */
    private boolean isCacheRequestSend = false;

    /**
     * Default constructor .. default health check frequency
     */
    public OFELinkBolt(TopologyConfig config) {
        super(BOLT_TICK_INTERVAL);

        this.islHealthCheckInterval = config.getDiscoveryInterval();
        this.islHealthCheckTimeout = config.getDiscoveryTimeout();
        this.islHealthFailureLimit = config.getDiscoveryLimit();

        watchDogInterval = config.getDiscoverySpeakerFailureTimeout();

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
            discoveryQueue = (LinkedList<DiscoveryNode>) payload;
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

        if (isOnline != isSpeakerAvailable) {
            if (isSpeakerAvailable) {
                logger.warn("Switch into ONLINE mode");
                isCacheRequestSend = false;
            } else {
                logger.warn("Switch into OFFLINE mode");
                isReceivedCacheInfo = false;
            }
        }
        isOnline = isSpeakerAvailable;

        if (! isOnline) {
            return;
        }

        if (!isCacheRequestSend)
        {
            // Only one message to FL needed
            isCacheRequestSend = true;
            sendNetworkRequest(tuple);
        }
        else if (isReceivedCacheInfo)
        {
            // On first tick(or after network outage), we send network dump request to FL,
            // and then we ignore all ticks till cache not received
            DiscoveryManager.Plan discoveryPlan = discovery.makeDiscoveryPlan();
            try {
                for (DiscoveryManager.Node node : discoveryPlan.needDiscovery) {
                    sendDiscoveryMessage(tuple, node);
                }

                for (DiscoveryManager.Node node : discoveryPlan.discoveryFailure) {
                    // this is somewhat incongruous - we send failure to TE, but we send
                    // discovery to FL ..
                    // Reality is that the handleDiscovery/handleFailure below does the work
                    //
                    sendDiscoveryFailed(node.switchId, node.portId, tuple);
                }
            } catch (IOException e) {
                logger.error("Unable to encode message: {}", e);
            }
        }
    }

    /**
     * Send network dump request to FL
     */
    private void sendNetworkRequest(Tuple tuple) {
        logger.info("Send network dump request");

        try {
            CommandMessage command = new CommandMessage(new NetworkCommandData(),
                    System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID,
                    Destination.CONTROLLER);
            String json = Utils.MAPPER.writeValueAsString(command);
            collector.emit(islDiscoveryTopic, tuple, new Values(PAYLOAD, json));
        }
        catch (JsonProcessingException exception)
        {
            logger.error("Could not serialize network cache request", exception);
        }
    }

    /**
     * Helper method for sending an ISL Discovery Message
     */
    private void sendDiscoveryMessage(Tuple tuple, DiscoveryManager.Node node) throws IOException {
        String json = OFEMessageUtils.createIslDiscovery(node.switchId, node.portId);
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
        try {
            BaseMessage bm = MAPPER.readValue(json, BaseMessage.class);
            watchDog.reset();

            if (bm instanceof InfoMessage) {
                InfoData data = ((InfoMessage)bm).getData();
                if (data instanceof NetworkSyncMarker) {
                    logger.info("Got response on network sync request, start processing network events");
                    isReceivedCacheInfo = true;
                } else if (!isReceivedCacheInfo) {
                    logger.warn("Bolt is not initialized mark skip tuple");
                } else if (data instanceof SwitchInfoData) {
                    handleSwitchEvent(tuple, (SwitchInfoData) data);
                    passToTopologyEngine(tuple);
                } else if (data instanceof PortInfoData) {
                    handlePortEvent(tuple, (PortInfoData) data);
                    passToTopologyEngine(tuple);
                } else if (data instanceof IslInfoData) {
                    handleIslEvent(tuple, (IslInfoData) data);
                } else {
                    logger.warn("Unknown InfoData type={}", data);
                }
            } else if (bm instanceof HeartBeat) {
                logger.debug("Got speaker's heart beat");
            }
        } catch (IOException e) {
            // All messages should be derived from BaseMessage .. so an exception here
            // means that we found something that isn't. If this criteria changes, then
            // change the logger level.
            logger.error("Unknown Message type={}", json);
            collector.ack(tuple);
        } finally {
            collector.ack(tuple);
        }
    }

    private void handleSwitchEvent(Tuple tuple, SwitchInfoData switchData) {
        String switchID = switchData.getSwitchId();
        String state = "" + switchData.getState();
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

    private void handlePortEvent(Tuple tuple, PortInfoData portData) {
        String switchID = portData.getSwitchId();
        String portID = "" + portData.getPortNo();
        String updown = "" + portData.getState();
        logger.info("DISCO: Port Event: switch={} port={} state={}", switchID, portID, updown);

        if (isPortUpOrCached(updown)) {
            discovery.handlePortUp(switchID, portID);
        } else if (updown.equals(OFEMessageUtils.PORT_DOWN)) {
            discovery.handlePortDown(switchID, portID);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("PORT Event: ignoring state: {}", updown);
        }
    }

    private void handleIslEvent(Tuple tuple, IslInfoData discoveredIsl) {
        PathNode node = discoveredIsl.getPath().get(0);
        String switchID = node.getSwitchId();
        String portID = "" + node.getPortNo();
        IslChangeType state = discoveredIsl.getState();
        boolean stateChanged = false;

        /*
         * TODO: would be good to merge more of this behavior / business logic within DiscoveryManager
         *  The reason is so that we consolidate behavior related to Network Topology Discovery into
         *  one place.
         */
        if (IslChangeType.DISCOVERED.equals(state)) {
            stateChanged = discovery.handleDiscovered(switchID, portID);
            // If the state has changed, and since we've discovered one end of an ISL, let's make
            // sure we can test the other side as well.
            if (stateChanged && discoveredIsl.getPath().size() > 1) {
                String dstSwitch = discoveredIsl.getPath().get(0).getSwitchId();
                String dstPort = ""+discoveredIsl.getPath().get(0).getPortNo();
                if (!discovery.checkForIsl(dstSwitch,dstPort)){
                    // Only call PortUp if we aren't checking for ISL. Otherwise, we could end up in an
                    // infinite cycle of always sending a Port UP when one side is discovered.
                    discovery.handlePortUp(dstSwitch,dstPort);
                }

            }

        } else if (IslChangeType.FAILED.equals(state)) {
            stateChanged = discovery.handleFailed(switchID, portID);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("ISL Event: ignoring state: {}", state);
        }

        if (stateChanged) {
            // If the state changed, notify the TE.
            logger.info("DISCO: ISL Event: switch={} port={} state={}", switchID, portID, state);
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
    private void sendDiscoveryFailed(String switchId, String portId, Tuple tuple) throws IOException {
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

        List<DiscoveryNode> filteredDiscoveryQueue =  discoveryQueue.stream().
                filter(node -> node.getSwitchId().equals(switchId)).
                collect(Collectors.toList());

        Set<DiscoveryNode> filterdIslFilter = islFilter.getMatchSet().stream().
                filter(node -> node.getSwitchId().equals(switchId)).
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
    List<DiscoveryNode> getDiscoveryQueue()
    {
        return this.discoveryQueue;
    }
}
