package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.wfm.topology.AbstractTopology;
import org.bitbucket.openkilda.wfm.topology.flow.ComponentType;
import org.bitbucket.openkilda.wfm.topology.flow.FlowTopology;
import org.bitbucket.openkilda.wfm.topology.flow.StreamType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

/**
 * Error Bolt. Processes error messages and forms Northbound responses.
 */
public class ErrorBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(ErrorBolt.class);

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        ErrorType errorType = (ErrorType) tuple.getValueByField(FlowTopology.ERROR_TYPE_FIELD);
        ErrorMessage error = (ErrorMessage) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
        error.setDestination(Destination.NORTHBOUND);
        Values values = new Values(error);

        try {
            logger.debug("Request tuple={}", tuple);

            switch (componentId) {
                case CRUD_BOLT:
                case SPLITTER_BOLT:
                    logger.debug("Error message: data={}", error.getData());
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
                    break;
                default:
                    logger.debug("Skip message from unknown component: component={}, stream={}, error-type={}",
                            componentId, streamId, errorType);
                    break;
            }
        } catch (Exception exception) {
            logger.error("Could not process message: {}", tuple, exception);
        } finally {
            logger.debug("Error message ack: component={}, stream={}, tuple={}, values={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple, values);

            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), AbstractTopology.fieldMessage);
    }
}
