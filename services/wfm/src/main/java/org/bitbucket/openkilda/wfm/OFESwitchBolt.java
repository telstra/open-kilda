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

import java.util.Map;

/**
 * OFESwitchBolt - OpenFlow Event for Switches Bolt
 *
 * Progress:
 *  - Initial implementation will just emit the event and place on the configured topic.
 *  - Future implementation may introduce flapping controls and/or other logic surrounding
 *      switch up/down event.
 */
public class OFESwitchBolt extends BaseStatefulBolt<KeyValueState<String, String>> {

    /** The ID of the Stream that this bolt will emit */
    private static Logger logger = LogManager.getLogger(OFESwitchBolt.class);

    protected OutputCollector collector;
    protected KeyValueState<String, String> state;
    public String outputStreamId = "kilda.wfm.topo.updown";

    public OFESwitchBolt withOutputStreamId(String outputStreamId){
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void initState(KeyValueState<String, String> state) {
        this.state = state;
    }

    /**
     * We expect to receive Switch up/down events. We'll track the Switches we've seen initially.
     *
     * This bolt will listen to the splitter kafka stream.
     *
     * @param input
     */
    @Override
    public void execute(Tuple input) {
        String switchID = getSwitchID(input);
        String history = state.get(switchID);
        if (history == null) {
            logger.debug("NEW SWITCH: " + switchID);
            state.put(switchID,switchID);
        } else {
            logger.debug("OLD SWITCH: " + switchID);
        }

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outputStreamId, new Fields("switch_id","state","timestamp"));
    }

    // ==================  ==================  ==================
    // HELPER FUNCTIONS
    // ==================  ==================  ==================

    private String getSwitchID(Tuple input) {
        String json = input.getString(0);
        System.out.println("json = " + json);
        return "";
    }

}
