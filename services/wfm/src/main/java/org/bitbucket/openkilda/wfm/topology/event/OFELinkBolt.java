package org.bitbucket.openkilda.wfm.topology.event;

import org.bitbucket.openkilda.wfm.OFEMessageUtils;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is the main class for tracking links and generating health checks.
 * Regarding Storm's KeyValueState .. it doesn't have a keys() feature .. so there is
 * at this stage only one object in it, which holds hashmaps, etc.
 */
public class OFELinkBolt extends AbstractTickStatefulBolt<KeyValueState<String, LinkTracker>> {

    private static Logger logger = LogManager.getLogger(OFELinkBolt.class);

    public static final String DEFAULT_DISCO_TOPIC = "kilda-test";
    public static final String DEFAULT_OUTPUT_STREAM = "kilda.wfm.topo.updown";
    /** Default frequency of health checks (ISL health check), in seconds */
    public static final Integer DEFAULT_HEALTH_CHECK_FREQ = 3;

    protected OutputCollector collector;

    /** SwitchID -> PortIDs */
    protected KeyValueState<String, LinkTracker> state;
    protected LinkTracker links;

    public String outputStreamId = DEFAULT_OUTPUT_STREAM;
    /** This is the kafka queue that the controller will listen to for ISL Discovery requests */
    public String islDiscoTopic = DEFAULT_DISCO_TOPIC;

    /**
     * Default constructor .. default health check frequency
     */
    public OFELinkBolt() {
        super(DEFAULT_HEALTH_CHECK_FREQ);
        // TODO: read health check frequency from config file and/or storm key/value.
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
        links = this.state.get(DEFAULT_DISCO_TOPIC);
        if (links == null) {
            links = new LinkTracker();
            this.state.put(DEFAULT_DISCO_TOPIC, links);
        }
    }

    /**
     * Send ISL health checks for all links
     */
    @Override
    protected void doTick(Tuple tuple) {
        for (String switchID : links.getSwitches()) {
            for (String portID : links.getSwitchPorts(switchID).keySet()) {
                String discoJson = OFEMessageUtils.createIslDiscovery(switchID, portID);
                collector.emit(islDiscoTopic, tuple, new Values("payload", discoJson));

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
            // TODO: determine whether we'll receive these, and what to do with it.
            //       .. presumably up, but down? (probably not down)
            logger.debug("LINK: ISL Event: {}", tuple);
        } else {
            logger.error("Unknown source component: {}", source);
        }
        collector.ack(tuple);
    }

    protected void handleSwitchEvent(Tuple tuple) {
        String switchID = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String updown = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);
        logger.debug("LINK: SWITCH EVENT {} / {}", switchID, updown);
        ConcurrentHashMap<String, String> ports = links.getOrNewSwitchPorts(switchID);
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
        logger.debug("LINK: PORT EVENT {} {} {}", switchID, portID, updown);

        ConcurrentHashMap<String, String> ports = links.getOrNewSwitchPorts(switchID);
        if (updown.equals(OFEMessageUtils.PORT_UP) || updown.equals(OFEMessageUtils.PORT_ADD)) {
            // Send ISL Discovery Packet
            String discoJson = OFEMessageUtils.createIslDiscovery(switchID, portID);
            collector.emit(islDiscoTopic, tuple, new Values("payload", discoJson));
            // TODO: will we put the link info?
            // TODO: check if port already exists? is there business logic (UP on existing port)
            ports.put(portID, portID);
        } else if (updown.equals(OFEMessageUtils.PORT_DOWN)) {
            // Clear the check, if it exists.
            logger.trace("LINK: REMOVING Port from health checks: {}:{}", switchID, portID);
            ports.remove(portID);
        } else {
            logger.error("LINK: PORT EVENT: Unknown state type: {}", updown);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outputStreamId, new Fields("key", "message"));
        declarer.declareStream(islDiscoTopic, new Fields("key", "message"));
    }

}
