package org.bitbucket.openkilda.wfm.topology.cache;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.info.InfoData;
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
     * Network cache cache.
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

        String json = tuple.getString(0);
        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());

        try {
            switch (componentId) {

                case STATE_UPDATE_KAFKA_SPOUT:

                    InfoData data = MAPPER.readValue(json, InfoData.class);

                    if (data instanceof InfoData) {
                        logger.debug("State update info message {}", message);

                        Message message = new InfoMessage(data, System.currentTimeMillis(),
                                DEFAULT_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
                        Values values = new Values(MAPPER.writeValueAsString(message));
                        outputCollector.emit(StreamType.STORE.toString(), tuple, values);

                        // TODO: update cache

                    } else {
                        logger.warn("Skip undefined message type {}", json);
                    }

                    break;

                case STATE_STORAGE_KAFKA_SPOUT:
                    logger.debug("Storage content message {}", json);

                    // TODO: fill states

                    break;

                case STATE_DUMP_KAFKA_SPOUT:
                    logger.debug("Dump cache command message {}", json);

                    // TODO: dump cache

                    break;

                default:
                    logger.warn("Skip undefined cache {}", tuple);

                    break;
            }

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } finally {
            logger.debug("State ack: component={}", componentId);

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
