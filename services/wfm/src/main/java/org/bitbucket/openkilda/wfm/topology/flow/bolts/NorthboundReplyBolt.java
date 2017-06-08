package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.fieldMessage;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.wfm.topology.flow.ComponentType;
import org.bitbucket.openkilda.wfm.topology.flow.StreamType;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Northbound Reply Bolt.
 * Forms northbound replies.
 */
public class NorthboundReplyBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(NorthboundReplyBolt.class);

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        logger.debug("Ingoing tuple: {}", tuple);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        Message message = (Message) tuple.getValueByField(MESSAGE_FIELD);
        CommandMessage commandMessage;
        CommandData commandData;
        InfoMessage infoMessage;
        InfoData infoData;
        FlowPayload flow;
        Values values;

        try {
            switch (streamId) {

                case CREATE:
                    commandMessage = (CommandMessage) message;
                    commandData = commandMessage.getData();
                    flow = ((FlowCreateRequest) commandData).getPayload();
                    infoData = new FlowResponse(flow);
                    infoMessage = new InfoMessage(infoData, commandMessage.getTimestamp(),
                            commandMessage.getCorrelationId());

                    logger.debug("Flow create response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    infoData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(infoMessage));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);

                    commandData.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(commandMessage));
                    outputCollector.emit(StreamType.CREATE.toString(), tuple, values);
                    break;

                case DELETE:
                    commandMessage = (CommandMessage) message;
                    commandData = commandMessage.getData();
                    FlowIdStatusPayload flowId = ((FlowDeleteRequest) commandData).getPayload();
                    infoData = new FlowStatusResponse(flowId);
                    infoMessage = new InfoMessage(infoData, commandMessage.getTimestamp(),
                            commandMessage.getCorrelationId());

                    logger.debug("Flow delete response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    infoData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(infoMessage));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);

                    commandData.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(commandMessage));
                    outputCollector.emit(StreamType.DELETE.toString(), tuple, values);
                    break;

                case UPDATE:
                    commandMessage = (CommandMessage) message;
                    commandData = commandMessage.getData();
                    flow = ((FlowUpdateRequest) commandData).getPayload();
                    infoData = new FlowResponse(flow);
                    infoMessage = new InfoMessage(infoData, commandMessage.getTimestamp(),
                            commandMessage.getCorrelationId());

                    logger.debug("Flow update response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    infoData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(infoMessage));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);

                    commandData.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(commandMessage));
                    outputCollector.emit(StreamType.UPDATE.toString(), tuple, values);
                    break;

                case READ:
                    infoMessage = (InfoMessage) message;
                    infoData = infoMessage.getData();

                    logger.debug("Flow get response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    infoData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(infoMessage));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
                    break;

                case PATH:
                    infoMessage = (InfoMessage) message;
                    infoData = infoMessage.getData();

                    logger.debug("Flow path response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    infoData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(message));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
                    break;

                case STATUS:
                    infoMessage = (InfoMessage) message;
                    infoData = infoMessage.getData();

                    logger.debug("Flow status response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    infoData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(infoMessage));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
                    break;

                case ERROR:
                    ErrorMessage error = (ErrorMessage) message;
                    ErrorData errorData = error.getData();

                    logger.debug("Flow error response: {}={}, component={}, stream={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    errorData.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(error));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
                    break;
            }
        } catch (JsonProcessingException exception) {
            logger.error("Could not serialize message: component={}, stream={}, message={}",
                    componentId, streamId, message);
        } finally {
            logger.debug("Flow response ack: component={}, stream={}", componentId, streamId);

            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.UPDATE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), fieldMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}

