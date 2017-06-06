package org.bitbucket.openkilda.wfm.topology.event;

import org.bitbucket.openkilda.wfm.OFEMessageUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    public static final String INFO = "speaker.info";
    public static final String COMMAND = "speaker.command";
    public static final String OTHER = "speaker.other";
    public static final String JSON_INFO = "info";
    public static final String JSON_COMMAND = "command";
    public static final String[] CHANNELS = {INFO, COMMAND, OTHER};
    private static Logger logger = LogManager.getLogger(OFEventSplitterBolt.class);
    OutputCollector _collector;

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String json = tuple.getString(0);
        try {
            Map<String, ?> root = OFEMessageUtils.fromJson(json);
            String type = ((String) root.get("type")).toLowerCase();
            Map<String, ?> data = (Map<String, ?>) root.get("payload");
            // TODO: data should be converted back to json string .. or use json serializer
            Values dataVal = new Values("payload", OFEMessageUtils.toJson(data));
            switch (type) {
                case JSON_INFO:
                    _collector.emit(INFO, tuple, dataVal);
                    break;
                case JSON_COMMAND:
                    _collector.emit(COMMAND, tuple, dataVal);
                    break;
                default:
                    // NB: we'll push the original message onto the CONFUSED channel
                    _collector.emit(OTHER, tuple, new Values("payload", json));
                    logger.warn("WARNING: Unknown Message Type: " + type);
            }
        } catch (IOException e) {
            logger.warn("EXCEPTION during JSON parsing: {}, error: {}", json, e.getMessage());
            e.printStackTrace();
        } finally {
            // Regardless of whether we have errors, we don't want to reprocess for now, so send ack
            _collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(INFO, new Fields("key", "message"));
        declarer.declareStream(COMMAND, new Fields("key", "message"));
        declarer.declareStream(OTHER, new Fields("key", "message"));
    }

}
