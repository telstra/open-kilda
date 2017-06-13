package org.bitbucket.openkilda.wfm.topology.event;

import static org.bitbucket.openkilda.messaging.Utils.PAYLOAD;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * InfoEventSplitterBolt - split the INFO message types
 * - All SWITCH / PORT / ISL messages will be sent to their respective queues
 * - UP / DOWN for each of SWITCH/PORT/ISL will be sent to a their respective queues
 * - OTHER will be used for each are if there isn't a match (INFO.OTHER,SWITCH.OTHER, etc.)
 */
public class InfoEventSplitterBolt extends BaseRichBolt {

    public static final String INFO = OFEventSplitterBolt.INFO; // topic created elsewhere
    public static final String I_OTHER = INFO + ".other";
    public static final String I_SWITCH = INFO + ".switch";
    public static final String I_SWITCH_UPDOWN = I_SWITCH + ".updown";
    public static final String I_SWITCH_OTHER = I_SWITCH + ".other";
    public static final String I_PORT = INFO + ".port";
    public static final String I_PORT_UPDOWN = I_PORT + ".updown";
    public static final String I_PORT_OTHER = I_PORT + ".other";
    public static final String I_ISL = INFO + ".isl";
    public static final String I_ISL_UPDOWN = I_ISL + ".updown";
    public static final String I_ISL_OTHER = I_ISL + ".other";
    public static final String[] outputStreams = {
            I_OTHER,
            I_SWITCH, I_SWITCH_UPDOWN, I_SWITCH_OTHER,
            I_PORT, I_PORT_UPDOWN, I_PORT_OTHER,
            I_ISL, I_ISL_UPDOWN, I_ISL_OTHER
    };
    private static Logger logger = LogManager.getLogger(InfoEventSplitterBolt.class);
    OutputCollector _collector;

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    /**
     * The data field holds the "message_type" and "state" fields.
     *
     * @param root the "payload" field of an "INFO" message
     */
    private void splitInfoMessage(Map<String, ?> root, Tuple tuple) throws JsonProcessingException {
        Values dataVal = new Values(PAYLOAD, new ObjectMapper().writeValueAsString(root));
        String key = ((String) root.get("message_type")).toLowerCase();
        String state = (String) root.get("state");
        switch (key) {
            case "switch":
                _collector.emit(I_SWITCH, tuple, dataVal);
                logger.debug("EMIT {} : {}", I_SWITCH, dataVal);
                if (state.equals("ACTIVATED") || state.equals("DEACTIVATED")) {
                    _collector.emit(I_SWITCH_UPDOWN, tuple, dataVal);
                    logger.debug("EMIT {} : {}", I_SWITCH_UPDOWN, dataVal);
                } else {
                    _collector.emit(I_SWITCH_OTHER, tuple, dataVal);
                    logger.debug("EMIT {} : {}", I_SWITCH_OTHER, dataVal);
                }
                break;
            case "port":
                _collector.emit(I_PORT, tuple, dataVal);
                logger.debug("EMIT {} : {}", I_PORT, dataVal);
                if (state.equals("UP") || state.equals("DOWN") || state.equals("ADD")) {
                    _collector.emit(I_PORT_UPDOWN, tuple, dataVal);
                } else {
                    _collector.emit(I_PORT_OTHER, tuple, dataVal);
                }
                break;
            case "isl":
                _collector.emit(I_ISL, tuple, dataVal);
                logger.debug("EMIT {} : {}", I_ISL, dataVal);
                // TODO: ISL doesn't seem to have a state field .. so it'll all go into other
                if (state != null && (state.equals("UP") || state.equals("DOWN"))) {
                    _collector.emit(I_ISL_UPDOWN, tuple, dataVal);
                } else {
                    _collector.emit(I_ISL_OTHER, tuple, dataVal);
                }
                break;
            default:
                // NB: we'll push the original message onto the CONFUSED channel
                _collector.emit(I_OTHER, tuple, dataVal);
                logger.warn("Unknown INFO Message Type: {}\nJSON:{}", key, root);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Tuple tuple) {
        String json = tuple.getStringByField("message");
        logger.info("Processing INFO message: {}", json);

        Map<String, ?> root = null;
        try {
            root = (Map<String, ?>) new ObjectMapper().readValue(json, Map.class);
            splitInfoMessage(root, tuple);
        } catch (IOException e) {
            logger.error("IOException processing an INFO message: {}, error: {} ", json,
                    e.getMessage());
            e.printStackTrace();
        } finally {
            // Regardless of whether we have errors, we don't want to reprocess for now, so send ack
            _collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields f = new Fields("key", "message");
        for (String name : outputStreams) {
            logger.trace("Declaring Stream: " + name);
            declarer.declareStream(name, f);
        }
    }
}

