package org.bitbucket.openkilda.wfm.topology.event;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.messaging.Utils.PAYLOAD;
import static org.bitbucket.openkilda.wfm.topology.event.OFEventWFMTopology.DEFAULT_DISCOVERY_TOPIC;
import static org.bitbucket.openkilda.wfm.topology.event.OFEventWFMTopology.DEFAULT_KAFKA_OUTPUT;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.wfm.OFEMessageUtils;
import org.bitbucket.openkilda.wfm.topology.splitter.InfoEventSplitterBolt;
import org.bitbucket.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;
import org.bitbucket.openkilda.wfm.topology.utils.LinkTracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is the main class for tracking links and generating health checks. Regarding Storm's KeyValueState .. it
 * doesn't have a keys() feature .. so there is at this stage only one object in it, which holds hashmaps, etc.
 */
public class OFELinkBolt extends AbstractTickStatefulBolt<KeyValueState<String, LinkTracker>> {
    private static final Logger logger = LogManager.getLogger(OFELinkBolt.class);

    public String outputStreamId = DEFAULT_KAFKA_OUTPUT;
    public String islDiscoTopic = DEFAULT_DISCOVERY_TOPIC;

    /** SwitchID -> PortIDs */
    protected KeyValueState<String, LinkTracker> state;

    private final int packetsToFail;
    private OutputCollector collector;
    private LinkTracker links;

    /**
     * Default constructor .. default health check frequency
     */
    public OFELinkBolt(int discoveryInterval, int discoveryTimeout) {
        super(discoveryInterval);
        // TODO: read health check frequency from config file and/or storm key/value.
        this.packetsToFail = discoveryTimeout / discoveryInterval;
    }

    public OFELinkBolt withOutputStreamId(String outputStreamId) {
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void initState(KeyValueState<String, LinkTracker> state) {
        this.state = state;
        // NB: First time the worker is created this will be null
        // TODO: what happens to state as workers go up or down
        links = this.state.get(DEFAULT_DISCOVERY_TOPIC);
        if (links == null) {
            links = new LinkTracker();
            this.state.put(DEFAULT_DISCOVERY_TOPIC, links);
        }
    }

    /**
     * Send ISL health checks for all links
     */
    @Override
    protected void doTick(Tuple tuple) {
        for (String switchId : links.getSwitches()) {
            for (Iterator<Map.Entry<String, AtomicInteger>> it = links.getSwitchPorts(switchId).entrySet().iterator(); it.hasNext(); ) {
                try {
                    Map.Entry<String, AtomicInteger> entry = it.next();

                    if (entry.getValue().get() == -1) {
                        sendDiscoveryFailed(switchId, entry.getKey(), tuple);
                        it.remove();
                    } else if (entry.getValue().incrementAndGet() >= packetsToFail) {
                        sendDiscoveryFailed(switchId, entry.getKey(), tuple);
                    }

                    String discoJson = OFEMessageUtils.createIslDiscovery(switchId, entry.getKey());
                    collector.emit(islDiscoTopic, tuple, new Values(PAYLOAD, discoJson));
                    logger.debug("LINK: Send ISL discovery command: {}", discoJson);
                } catch (IOException exception) {
                    logger.error("LINK: ISL discovery failure message creation error", exception);
                }
            }
        }
    }

    @Override
    protected void doWork(Tuple tuple) {
        String source = tuple.getSourceComponent();
        if (source.startsWith(InfoEventSplitterBolt.I_SWITCH_UPDOWN)) {
            handleSwitchEvent(tuple);
        } else if (source.startsWith(InfoEventSplitterBolt.I_PORT_UPDOWN)) {
            handlePortEvent(tuple);
        } else if (source.startsWith(InfoEventSplitterBolt.I_ISL_UPDOWN)) {
            handleIslEvent(tuple);
        } else {
            logger.error("LINK: Unknown source component={}", source);
        }
        collector.ack(tuple);
    }

    protected void handleSwitchEvent(Tuple tuple) {
        String switchID = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String updown = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);
        logger.info("LINK: Event switch={} state={}", switchID, updown);
        ConcurrentHashMap<String, AtomicInteger> ports = links.getOrNewSwitchPorts(switchID);
        if (updown.equals(OFEMessageUtils.SWITCH_DOWN)) {
            // current logic: switch down means stop checking associated ports/links.
            // - possible extra steps of validation of switch down should occur elsewhere
            // - possible extra steps of generating link down messages aren't important since
            //      the TPE will drop the switch node from its graph.
            // switch up isn't valuable at this stage.
            ports.clear();
        }
    }

    protected void handlePortEvent(Tuple tuple) {
        String switchID = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String portID = tuple.getStringByField(OFEMessageUtils.FIELD_PORT_ID);
        String updown = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);
        logger.info("LINK: Event switch={} port={} state={}", switchID, portID, updown);

        ConcurrentHashMap<String, AtomicInteger> ports = links.getOrNewSwitchPorts(switchID);
        if (updown.equals(OFEMessageUtils.PORT_UP) || updown.equals(OFEMessageUtils.PORT_ADD)) {
            // Send ISL Discovery Packet
            String discoJson = OFEMessageUtils.createIslDiscovery(switchID, portID);
            collector.emit(islDiscoTopic, tuple, new Values(PAYLOAD, discoJson));
            logger.debug("LINK: Send ISL discovery command: {}", discoJson);
            // TODO: will we put the link info?
            // TODO: check if port already exists? is there business logic (UP on existing port)
            ports.put(portID, new AtomicInteger(0));
        } else if (updown.equals(OFEMessageUtils.PORT_DOWN)) {
            // Clear the check, if it exists.
            logger.info("LINK: Remove switch={} port={} from health checks", switchID, portID);
            String discoJson = OFEMessageUtils.createIslDiscovery(switchID, portID);
            collector.emit(islDiscoTopic, tuple, new Values(PAYLOAD, discoJson));
            ports.get(portID).set(-1);
        } else {
            logger.error("LINK: Unknown state={} for switch={} port={}", updown, switchID, portID);
        }
    }

    protected void handleIslEvent(Tuple tuple) {
        logger.info("LINK: Event ISL Discovered {}", tuple);
        try {
            String data = tuple.getString(0);
            IslInfoData discoveredIsl = MAPPER.readValue(data, IslInfoData.class);
            PathNode node = discoveredIsl.getPath().get(0);
            links.clearCountOfSentPackets(node.getSwitchId(), String.valueOf(node.getPortNo()));
            Values dataVal = new Values(PAYLOAD, data, node.getSwitchId(),
                    String.valueOf(node.getPortNo()), OFEMessageUtils.LINK_UP);
            collector.emit(outputStreamId, tuple, dataVal);
        } catch (IOException exception) {
            logger.error("LINK: ISL discovered message deserialization failed", exception);
        }
    }

    private void sendDiscoveryFailed(String switchId, String portId, Tuple tuple) throws IOException {
        String discoFail = OFEMessageUtils.createIslFail(switchId, portId);
        Values dataVal = new Values(PAYLOAD, discoFail, switchId, portId, OFEMessageUtils.LINK_DOWN);
        collector.emit(outputStreamId, tuple, dataVal);
        logger.warn("LINK: Send ISL discovery failure message={}", discoFail);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(islDiscoTopic, new Fields("key", "message"));
        declarer.declareStream(outputStreamId, new Fields("key", "message",
                OFEMessageUtils.FIELD_SWITCH_ID, OFEMessageUtils.FIELD_PORT_ID, OFEMessageUtils.FIELD_STATE));
    }
}
