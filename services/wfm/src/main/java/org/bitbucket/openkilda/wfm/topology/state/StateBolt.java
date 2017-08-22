package org.bitbucket.openkilda.wfm.topology.state;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.info.InfoMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

public class StateBolt extends BaseStatefulBolt<InMemoryKeyValueState<String, String>> {
    private static final String MESSAGE_FIELD = "message";
    private static final Fields fieldMessage = new Fields(MESSAGE_FIELD);

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(StateBolt.class);

    /**
     * Network state state.
     */
    private InMemoryKeyValueState<String, String> state;

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    @Override
    public void initState(InMemoryKeyValueState<String, String> state) {
        this.state = state;
        // TODO: send storage request
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }

    @Override
    public void execute(Tuple tuple) {
        logger.trace("State before: {}", state);
        logger.debug("Ingoing tuple: {}", tuple);

        String json;
        Values values;
        Message message;

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());

        try {
            switch (componentId) {

                case STATE_UPDATE_KAFKA_SPOUT:

                    json = tuple.getStringByField("message");
                    message = MAPPER.readValue(json, Message.class);

                    if (message instanceof CommandMessage) {
                        logger.debug("Dump state command message {}", message);

                        // TODO: dump state

                    } else if (message instanceof InfoMessage) {
                        logger.debug("State update info message {}", message);

                        //message.setDestination(Destination.TOPOLOGY_ENGINE);
                        values = new Values(MAPPER.writeValueAsString(message));
                        outputCollector.emit(StreamType.STORE.toString(), tuple, values);

                        // TODO: update state

                    } else {
                        logger.warn("Skip undefined message type {}", json);
                    }

                    break;

                case STATE_STORAGE_KAFKA_SPOUT:

                    json = tuple.getString(0);
                    logger.debug("Storage content message {}", json);

                    message = MAPPER.readValue(json, Message.class);

                    // TODO: fill states

                    break;

                default:
                    logger.warn("Skip undefined state {}", tuple);

                    break;
            }

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } finally {
            logger.debug("State ack: component={}, stream={}", componentId, streamId);

            outputCollector.ack(tuple);
        }

        logger.trace("State after: {}", state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.STORE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.DUMP.toString(), fieldMessage);
    }


}
