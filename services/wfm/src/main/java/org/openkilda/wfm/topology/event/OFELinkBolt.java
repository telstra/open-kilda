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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.wfm.OFEMessageUtils;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.isl.DiscoveryManager;
import org.openkilda.wfm.isl.DiscoveryNode;
import org.openkilda.wfm.isl.DummyIIslFilter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.TopologyConfig;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

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
 */
public class OFELinkBolt
        extends AbstractTickStatefulBolt<KeyValueState<String, Object>>
        implements ICtrlBolt {
    private static final Logger logger = LoggerFactory.getLogger(OFELinkBolt.class);

    private final String STREAM_ID_CTRL = "ctrl";
    private final String STATE_ID_DISCOVERY = "discovery-manager";
    private final String topoEngTopic;
    private final String islDiscoveryTopic;

    private final int packetsToFail;
    private TopologyContext context;
    private OutputCollector collector;

    private DummyIIslFilter islFilter;
    private DiscoveryManager discovery;
    private LinkedList<DiscoveryNode> discoveryQueue;

    /**
     * Default constructor .. default health check frequency
     */
    public OFELinkBolt(TopologyConfig config) {
        super(config.getDiscoveryInterval());

        packetsToFail = config.getDiscoveryTimeout() / config.getDiscoveryInterval();
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
        // NB: First time the worker is created this will be null
        // TODO: what happens to state as workers go up or down

        Object payload = state.get(STATE_ID_DISCOVERY);
        if (payload == null) {
            payload = discoveryQueue = new LinkedList<>();
            state.put(islDiscoveryTopic, payload);
        } else {
            discoveryQueue = (LinkedList<DiscoveryNode>) payload;
        }

        discovery = new DiscoveryManager(islFilter, discoveryQueue, packetsToFail);
    }

    /**
     * Send ISL health checks for all links
     */
    @Override
    protected void doTick(Tuple tuple) {
        DiscoveryManager.Plan discoveryPlan = discovery.makeDiscoveryPlan();
        try {
            for (DiscoveryManager.Node node : discoveryPlan.needDiscovery) {
                sendDiscoveryMessage(tuple, node);
            }

            for (DiscoveryManager.Node node : discoveryPlan.discoveryFailure) {
                    sendDiscoveryFailed(node.switchId, node.portId, tuple);
            }
        } catch (IOException e) {
            logger.error("Unable to encode message: {}", e);
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
            if (bm instanceof InfoMessage) {
                InfoData data = ((InfoMessage)bm).getData();
                if (data instanceof SwitchInfoData) {
                    handleSwitchEvent(tuple, (SwitchInfoData) data);
                } else if (data instanceof PortInfoData) {
                    handlePortEvent(tuple, (PortInfoData) data);
                } else if (data instanceof IslInfoData) {
                    handleIslEvent(tuple, (IslInfoData) data);
                } else {
                    logger.warn("Unknown InfoData type={}", data);
                }
            }
        } catch (IOException e) {
            // All messages should be derived from BaseMessage .. so an exception here
            // means that we found something that isn't. If this criteria changes, then
            // change the logger level.
            logger.error("Unknown Message type={}", json);
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
            discovery.handleSwitchUp(switchID);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("SWITCH Event: ignoring state: {}", state);
        }

        // Pass the original message along, to the Topology Engine topic.
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

        // Pass the original message along, to the Topology Engine topic.
        String json = tuple.getString(0);
        collector.emit(topoEngTopic, tuple, new Values(PAYLOAD, json));
    }

    private void handleIslEvent(Tuple tuple, IslInfoData discoveredIsl) {
        PathNode node = discoveredIsl.getPath().get(0);
        String switchID = node.getSwitchId();
        String portID = "" + node.getPortNo();
        IslChangeType state = discoveredIsl.getState();
        logger.info("DISCO: ISL Event: switch={} port={} state={}", switchID, portID, state);

        if (IslChangeType.DISCOVERED.equals(state)) {
            discovery.handleDiscovered(switchID, portID);
        } else if (IslChangeType.FAILED.equals(state)) {
            discovery.handleFailed(switchID, portID);
        } else {
            // TODO: Should this be a warning? Evaluate whether any other state needs to be handled
            logger.warn("ISL Event: ignoring state: {}", state);
        }

        String json = tuple.getString(0);
        collector.emit(topoEngTopic, tuple, new Values(PAYLOAD, json));
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
        Values dataVal = new Values(PAYLOAD, discoFail, switchId, portId, OFEMessageUtils.LINK_DOWN);
        collector.emit(topoEngTopic, tuple, dataVal);
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
    public TopologyContext getContext() {
        return context;
    }

    @Override
    public OutputCollector getOutput() {
        return collector;
    }
}
