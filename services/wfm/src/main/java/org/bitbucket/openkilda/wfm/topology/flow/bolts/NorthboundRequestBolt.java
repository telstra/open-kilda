package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageErrorType;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageFlowId;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.wfm.topology.flow.StreamType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

/**
 * Northbound Request Bolt.
 * Handles northbound requests.
 */
public class NorthboundRequestBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(NorthboundRequestBolt.class);

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

        String request = tuple.getString(0);
        //String request = tuple.getStringByField("value");
        Values values = new Values(request);

        try {
            Message message = MAPPER.readValue(request, Message.class);
            if (!Destination.WFM.equals(message.getDestination()) || !(message instanceof CommandMessage)) {
                return;
            }
            CommandData data = ((CommandMessage) message).getData();

            if (data instanceof FlowCreateRequest) {
                String flowId = ((FlowCreateRequest) data).getPayload().getId();

                logger.debug("Flow {} crate message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

            } else if (data instanceof FlowDeleteRequest) {
                String flowId = ((FlowDeleteRequest) data).getPayload().getId();

                logger.debug("Flow {} delete message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

            } else if (data instanceof FlowUpdateRequest) {
                String flowId = ((FlowUpdateRequest) data).getPayload().getId();

                logger.debug("Flow {} update message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.UPDATE.toString(), tuple, values);

            } else if (data instanceof FlowStatusRequest) {
                String flowId = ((FlowStatusRequest) data).getPayload().getId();

                logger.debug("Flow {} status message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

            } else if (data instanceof FlowGetRequest) {
                String flowId = ((FlowGetRequest) data).getPayload().getId();

                logger.debug("Flow {} get message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.READ.toString(), tuple, values);

            } else if (data instanceof FlowsGetRequest) {
                logger.debug("Flows get message: values={}", values);

                values = new Values(message, null);
                outputCollector.emit(StreamType.READ.toString(), tuple, values);

            } else if (data instanceof FlowPathRequest) {
                String flowId = ((FlowPathRequest) data).getPayload().getId();

                logger.debug("Flow {} path message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.PATH.toString(), tuple, values);

            } else {
                logger.debug("Skip undefined message: {}={}", CORRELATION_ID, message.getCorrelationId());
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message={}", request, exception);

            values = new Values(request, ErrorType.REQUEST_INVALID);
            outputCollector.emit(StreamType.ERROR.toString(), tuple, values);

        } finally {
            logger.debug("Northbound message ack: values={}", values);

            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.READ.toString(), fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.UPDATE.toString(), fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.PATH.toString(), fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.ERROR.toString(), fieldsMessageErrorType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
