package org.openkilda.wfm.ctrl;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.openkilda.wfm.IKildaBolt;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.error.StreamNameCollisionException;

import java.util.HashMap;
import java.util.Map;

public class RouteBolt extends BaseRichBolt implements IKildaBolt {
    final private String PREFIX_STREAM_ID = "ctrl.";
    final public String STREAM_ID_ERROR = PREFIX_STREAM_ID + "_error";

    String topologyName;
    TopologyContext context;
    OutputCollector output;

    HashMap<String, String> endpointMapping;

    public RouteBolt(String topology) {
        topologyName = topology;

        endpointMapping = new HashMap<>();
    }

    public String registerEndpoint(String boltId) throws StreamNameCollisionException {
        if (endpointMapping.containsKey(boltId)) {
            throw new StreamNameCollisionException();
        }

        String stream = PREFIX_STREAM_ID + boltId;
        endpointMapping.put(boltId, stream);

        return stream;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.context = context;
        this.output = collector;
    }

    @Override
    public void execute(Tuple input) {
        RouteAction action = new RouteAction(
                this, input, topologyName, endpointMapping);
        action.run();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STREAM_ID_ERROR, KafkaMessage.FORMAT);
        for (String streamId : endpointMapping.values()) {
            declarer.declareStream(streamId, RouteMessage.FORMAT);
        }
    }

    @Override
    public TopologyContext getContext() {
        return context;
    }

    @Override
    public OutputCollector getOutput() {
        return output;
    }
}
