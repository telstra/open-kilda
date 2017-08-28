package org.bitbucket.openkilda.wfm.topology.cache;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.MessageData;
import org.bitbucket.openkilda.messaging.command.discovery.DumpNetwork;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.discovery.NetworkDump;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

public class StateBolt extends BaseStatefulBolt<InMemoryKeyValueState<String, String>> {
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

                case CACHE_UPDATE_KAFKA_SPOUT:
                    InfoData data = MAPPER.readValue(json, InfoData.class);

                    if (data != null) {
                        logger.debug("State update info data {}", data);

                        Message message = new InfoMessage(data, System.currentTimeMillis(),
                                DEFAULT_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);

                        Values values = new Values(MAPPER.writeValueAsString(message));

                        // TODO: update cache
                        outputCollector.emit(StreamType.CACHE_TPE.toString(), tuple, values);

                    } else {
                        logger.warn("Skip undefined message type {}", json);
                    }

                    break;

                case CACHE_STORAGE_KAFKA_SPOUT:
                    Message message = MAPPER.readValue(json, Message.class);

                    if (message instanceof InfoMessage && Destination.WFM_CACHE == message.getDestination()) {
                        logger.debug("Storage content message {}", json);

                        // TODO: fill states
                    }

                    break;

                case CACHE_DUMP_KAFKA_SPOUT:
                    MessageData messageData = MAPPER.readValue(json, MessageData.class);

                    if (messageData instanceof DumpNetwork) {
                        logger.debug("Dump cache command message {}", json);

                        NetworkDump dump = new NetworkDump(/* TODO: prepare dump */);
                        Values values = new Values(MAPPER.writeValueAsString(dump));
                        outputCollector.emit(StreamType.CACHE_WFM.toString(), tuple, values);
                    }

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
        outputFieldsDeclarer.declareStream(StreamType.CACHE_TPE.toString(), CacheTopology.MESSAGE_FIELDS);
        outputFieldsDeclarer.declareStream(StreamType.CACHE_WFM.toString(), CacheTopology.MESSAGE_FIELDS);
        outputFieldsDeclarer.declareStream(StreamType.CACHE_REDIS.toString(), CacheTopology.REDIS_SWITCH_FIELDS);
    }
}
