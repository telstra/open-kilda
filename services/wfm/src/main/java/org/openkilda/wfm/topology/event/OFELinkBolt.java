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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.info.event.IslInfoData;
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
import org.openkilda.wfm.topology.splitter.InfoEventSplitterBolt;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

/**
 * This class is the main class for tracking links and generating health checks. Regarding Storm's KeyValueState .. it
 * doesn't have a keys() feature .. so there is at this stage only one object in it, which holds hashmaps, etc.
 */
public class OFELinkBolt
        extends AbstractTickStatefulBolt<KeyValueState<String, Object>>
        implements ICtrlBolt {
    private static final Logger logger = LogManager.getLogger(OFELinkBolt.class);

    private final String STREAM_ID_CTRL = "ctrl";
    private final String STATE_ID_DISCOVERY = "discovery-manager";
    private final String outputStreamId;
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
        outputStreamId = config.getKafkaOutputTopic();
        islDiscoveryTopic = config.getKafkaDiscoveryTopic();
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
        for (DiscoveryManager.Node node : discoveryPlan.needDiscovery) {
            String json = OFEMessageUtils.createIslDiscovery(node.switchId, node.portId);
            logger.debug("LINK: Send ISL discovery command: {}", json);
            collector.emit(islDiscoveryTopic, tuple, new Values(PAYLOAD, json));
        }

        for (DiscoveryManager.Node node : discoveryPlan.discoveryFailure) {
            try {
                sendDiscoveryFailed(node.switchId, node.portId, tuple);
            } catch (IOException e) {
                logger.error("Unable to encode message: {}", e);
            }
        }
    }

    @Override
    protected void doWork(Tuple tuple) {
        if (CtrlAction.boltHandlerEntrance(this, tuple))
            return;

        String source = tuple.getSourceComponent();
        if (source.equals(OFEventWFMTopology.SPOUT_ID_INPUT)) {
            PopulateIslFilterAction action = new PopulateIslFilterAction(this, tuple, islFilter);
            action.run();
            return;
        }

        try {
            if (source.startsWith(InfoEventSplitterBolt.I_SWITCH_UPDOWN)) {
                handleSwitchEvent(tuple);
            } else if (source.startsWith(InfoEventSplitterBolt.I_PORT_UPDOWN)) {
                handlePortEvent(tuple);
            } else if (source.startsWith(InfoEventSplitterBolt.I_ISL_UPDOWN)) {
                handleIslEvent(tuple);
            } else {
                logger.error("LINK: Unknown source component={}", source);
            }
        } finally {
            collector.ack(tuple);
        }
    }

    private void handleSwitchEvent(Tuple tuple) {
        String switchID = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String state = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);
        logger.info("LINK: Event switch={} state={}", switchID, state);

        if (SwitchState.DEACTIVATED.getType().equals(state)) {
            // current logic: switch down means stop checking associated ports/links.
            // - possible extra steps of validation of switch down should occur elsewhere
            // - possible extra steps of generating link down messages aren't important since
            //      the TPE will drop the switch node from its graph.
            discovery.handleSwitchDown(switchID);
        } else if (SwitchState.ACTIVATED.getType().equals(state)) {
            discovery.handleSwitchUp(switchID);
        } else {
            logger.error("DATA ENCODING ISSUE: illegal switch up/down status: {}", state);
        }
    }

    private void handlePortEvent(Tuple tuple) {
        String switchID = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String portID = tuple.getStringByField(OFEMessageUtils.FIELD_PORT_ID);
        String updown = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);
        logger.info("LINK: Event switch={} port={} state={}", switchID, portID, updown);

        if (isPortUpOrCached(updown)) {
            discovery.handlePortUp(switchID, portID);
        } else if (updown.equals(OFEMessageUtils.PORT_DOWN)) {
            discovery.handlePortDown(switchID, portID);
        } else {
            logger.error("DATA ENCODING ISSUE: illegal port status: {}", updown);
        }
    }

    private void handleIslEvent(Tuple tuple) {
        logger.info("LINK: Event ISL Discovered {}", tuple);

        String data = tuple.getString(0);
        IslInfoData discoveredIsl;
        try {
            discoveredIsl = MAPPER.readValue(data, IslInfoData.class);
        } catch (IOException exception) {
            logger.error("LINK: ISL discovered message deserialization failed", exception);
            return;
        }

        PathNode node = discoveredIsl.getPath().get(0);
        discovery.handleDiscovered(node.getSwitchId(), String.valueOf(node.getPortNo()));

        Values dataVal = new Values(PAYLOAD, data, node.getSwitchId(),
                String.valueOf(node.getPortNo()), OFEMessageUtils.LINK_UP);
        collector.emit(outputStreamId, tuple, dataVal);
    }

    private void sendDiscoveryFailed(String switchId, String portId, Tuple tuple) throws IOException {
        String discoFail = OFEMessageUtils.createIslFail(switchId, portId);
        Values dataVal = new Values(PAYLOAD, discoFail, switchId, portId, OFEMessageUtils.LINK_DOWN);
        collector.emit(outputStreamId, tuple, dataVal);
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
        declarer.declareStream(outputStreamId, new Fields("key", "message",
                OFEMessageUtils.FIELD_SWITCH_ID, OFEMessageUtils.FIELD_PORT_ID, OFEMessageUtils.FIELD_STATE));
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
