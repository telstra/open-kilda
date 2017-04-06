package org.bitbucket.openkilda.wfm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class OFELinkBolt
        extends BaseStatefulBolt<KeyValueState<String, ConcurrentHashMap<String, String>>> {

    private static Logger logger = LogManager.getLogger(OFELinkBolt.class);
    protected OutputCollector collector;

    /** SwitchID -> PortIDs */
    protected KeyValueState<String, ConcurrentHashMap<String, String>> state;
    public String outputStreamId = "kilda.wfm.topo.updown";
    // TODO: change islDiscoId from kilda-test to something else when everything is integrated
    /** This is the kafka queue that the controller will listen to for ISL Discovery requests */
    public static final String DEFAULT_DISCO_TOPIC = "kilda-test";
    public String islDiscoTopic = DEFAULT_DISCO_TOPIC;
    /** ISL Discovery packets will be sent at this frequency */
    public long health_check_freq = 30*1000;

    public OFELinkBolt withOutputStreamId(String outputStreamId){
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void initState(KeyValueState<String, ConcurrentHashMap<String, String>> state) {
        this.state = state;
    }

    @Override
    public void execute(Tuple tuple) {
        String source = tuple.getSourceComponent();
        if (source.startsWith(InfoEventSplitterBolt.I_SWITCH_UPDOWN)){
            handleSwitchEvent(tuple);
        } else if (source.startsWith(InfoEventSplitterBolt.I_PORT_UPDOWN)){
            handlePortEvent(tuple);
        } else if (source.startsWith(InfoEventSplitterBolt.I_ISL_UPDOWN)) {
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
        ConcurrentHashMap<String, String> history = ensureSwitchMap(switchID);
        if (updown.equals(OFEMessageUtils.SWITCH_DOWN)){
            // current logic: switch down means stop checking associated ports/links.
            // - possible extra steps of validation of switch down should occur elsewhere
            // - possible extra steps of generating link down messages aren't important since
            //      the TPE will drop the switch node from its graph.
            // switch up isn't valuable at this stage.
            history.clear();
        }
    }

    protected void handlePortEvent(Tuple tuple) {
        String switchID = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String portID = tuple.getStringByField(OFEMessageUtils.FIELD_PORT_ID);
        String updown = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);
        logger.debug("LINK: PORT EVENT {} {} {}", switchID, portID, updown);

        ConcurrentHashMap<String, String> history = ensureSwitchMap(switchID);
        if (updown.equals(OFEMessageUtils.PORT_UP)){
            // Send ISL Discovery Packet
            String discoJson = OFEMessageUtils.createIslDiscovery(switchID,portID);
            collector.emit(islDiscoTopic, tuple, new Values("data",discoJson));
        } else if (updown.equals(OFEMessageUtils.PORT_DOWN)){
            // Clear the check, if it exists.
            logger.trace("LINK: REMOVING Port from health checks: {}:{}", switchID, portID);
            history.remove(portID);
        } else {
            logger.error("LINK: PORT EVENT: Unknown state type: {}", updown);
        }
    }

    private ConcurrentHashMap<String, String> ensureSwitchMap(String switchID){
        ConcurrentHashMap<String, String> history = state.get(switchID);
        if (history == null) {
            // regardless of up/down .. ensure we have a struct.
            history = new ConcurrentHashMap<>();
            state.put(switchID, history);
        }
        return history;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outputStreamId, new Fields("key","message"));
        declarer.declareStream(islDiscoTopic, new Fields("key","message"));
    }

}
