package org.bitbucket.openkilda.wfm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

/**
 * OFEventSplitterBolt - split the OpenFlow messages (ie INFO / COMMAND)
 */
public class OFEventSplitterBolt extends BaseRichBolt {
    OutputCollector _collector;

    public static final String INFO = "INFO";
    public static final String COMMAND = "COMMAND";
    public static final String OTHER = "OTHER";

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String json = tuple.getString(0);
        ObjectMapper mapper = new ObjectMapper();
        Map<String,?> root;
        try {
            root = mapper.readValue(json, Map.class);
            String type = (String) root.get("type");
            Map<String,?> data = (Map<String,?>) root.get("data");
            // TODO: data should be converted back to json string .. or use json serializer
            Values dataVal = new Values("data", mapper.writeValueAsString(data));
            switch (type) {
                case INFO:
                    _collector.emit(INFO,tuple,dataVal);
                    break;
                case COMMAND:
                    _collector.emit(COMMAND,tuple,dataVal);
                    break;
                default:
                    // NB: we'll push the original message onto the CONFUSED channel
                    _collector.emit(OTHER,tuple, new Values(tuple.getString(0)));
                    System.out.println("WARNING: Unknown Message Type: " + type);
            }
        } catch (IOException e) {
            System.out.println("ERROR: Exception during json parsing: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Regardless of whether we have errors, we don't want to reprocess for now, so send ack
        _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(INFO, new Fields("key","message"));
        declarer.declareStream(COMMAND, new Fields("key","message"));
        declarer.declareStream(OTHER, new Fields("key","message"));
    }

}
