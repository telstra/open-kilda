package org.bitbucket.openkilda.wfm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * InfoEventSplitterBolt - split the INFO message types
 * - All SWITCH / PORT / ISL messages will be sent to their respective queues
 * - UP / DOWN for each of SWITCH/PORT/ISL will be sent to a their respective queues
 * - OTHER will be used for each are if there isn't a match (INFO.OTHER,SWITCH.OTHER, etc.)
 */
public class InfoEventSplitterBolt extends BaseRichBolt {

    private static Logger logger = LoggerFactory.getLogger(InfoEventSplitterBolt.class);

    OutputCollector _collector;

    public static final String INFO_SWITCH = "INFO.SWITCH";
    public static final String INFO_PORT = "INFO.PORT";
    public static final String INFO_ISL = "INFO.ISL";
    public static final String INFO_OTHER = "INFO.OTHER";
    public static final String SWITCH_UPDOWN = "SWITCH.UPDOWN";
    public static final String PORT_UPDOWN = "PORT.UPDOWN";
    public static final String ISL_UPDOWN = "ISL.UPDOWN";
    public static final String SWITCH_OTHER = "SWITCH.OTHER";
    public static final String PORT_OTHER = "PORT.OTHER";
    public static final String ISL_OTHER = "ISL.OTHER";

    public static final String[] outputStreams = {
            INFO_SWITCH, INFO_PORT, INFO_ISL,
            SWITCH_UPDOWN, PORT_UPDOWN, ISL_UPDOWN,
            SWITCH_OTHER, PORT_OTHER, ISL_OTHER
    };

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    /**
     * The data field holds the "message_type" and "state" fields.
     * @param root the "data" field of an "INFO" message
     */
    private void splitInfoMessage(Map<String,?> root, Tuple tuple) throws JsonProcessingException {
        Values dataVal = new Values("data", new ObjectMapper().writeValueAsString(root));
        String key = ((String) root.get("message_type")).toUpperCase();
        String state = (String) root.get("state");
        switch (key) {
            case "SWITCH":
                _collector.emit(INFO_SWITCH,tuple,dataVal);
                logger.debug("EMIT {} : {}", INFO_SWITCH, dataVal);
                if (state.equals("ACTIVATED") || state.equals("DEACTIVATED")){
                    _collector.emit(SWITCH_UPDOWN,tuple,dataVal);
                    logger.debug("EMIT {} : {}", SWITCH_UPDOWN, dataVal);
                } else {
                    _collector.emit(SWITCH_OTHER,tuple,dataVal);
                    logger.debug("EMIT {} : {}", SWITCH_OTHER, dataVal);
                }
                break;
            case "PORT":
                _collector.emit(INFO_PORT,tuple,dataVal);
                if (state.equals("UP") || state.equals("DOWN")){
                    _collector.emit(PORT_UPDOWN,tuple,dataVal);
                } else {
                    _collector.emit(PORT_OTHER,tuple,dataVal);
                }
                break;
            case "ISL":
                _collector.emit(INFO_ISL,tuple,dataVal);
                if (state.equals("UP") || state.equals("DOWN")){
                    _collector.emit(ISL_UPDOWN,tuple,dataVal);
                } else {
                    _collector.emit(ISL_OTHER,tuple,dataVal);
                }
                break;
            default:
                // NB: we'll push the original message onto the CONFUSED channel
                _collector.emit(INFO_OTHER,tuple,dataVal);
                logger.warn("Unknown INFO Message Type: {}\nJSON:{}", key, root);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Tuple tuple) {
        String json = tuple.getString(1);

        Map<String,?> root = null;
        try {
            root = (Map<String,?>) new ObjectMapper().readValue(json, Map.class);
            splitInfoMessage(root,tuple);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Regardless of whether we have errors, we don't want to reprocess for now, so send ack
            _collector.ack(tuple);

        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields f = new Fields("key","message");
        for (String name : outputStreams) {
            logger.trace("Declaring Stream: " + name);
            declarer.declareStream(name, f);
        }
    }
}

