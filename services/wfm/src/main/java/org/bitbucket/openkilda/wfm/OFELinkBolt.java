package org.bitbucket.openkilda.wfm;

import org.apache.storm.state.State;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

/**
 *
 */
public class OFELinkBolt extends BaseStatefulBolt {

    public String outputStreamId = "kilda.wfm.topo.updown";

    public OFELinkBolt withOutputStreamId(String outputStreamId){
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void execute(Tuple input) {

    }

    @Override
    public void initState(State state) {

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outputStreamId, new Fields("switch_id","state","timestamp"));
    }


}
