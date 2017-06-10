package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsFlowStatus;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageErrorType;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageSwitchFlowTransaction;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.BaseInstallFlow;
import org.bitbucket.openkilda.messaging.command.flow.RemoveFlow;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.payload.flow.FlowStatusType;
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
 * Speaker Bolt.
 * Processes replies from OpenFlow Speaker service.
 */
public class SpeakerBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(SpeakerBolt.class);

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
        Values values;

        try {
            Message message = MAPPER.readValue(request, Message.class);
            if (!Destination.WFM_TRANSACTION.equals(message.getDestination())) {
                return;
            }

            if (message instanceof CommandMessage) {
                CommandData data = ((CommandMessage) message).getData();

                if (data instanceof BaseInstallFlow) {
                    Long transactionId = ((BaseInstallFlow) data).getTransactionId();
                    String switchId = ((BaseInstallFlow) data).getSwitchId();
                    String flowId = ((BaseInstallFlow) data).getId();

                    logger.debug("Flow install message: {}={}, switch-id={}, flow-id={}, transaction-id={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), switchId, flowId, transactionId, request);

                    message.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(message), switchId, flowId, transactionId);
                    outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

                } else if (data instanceof RemoveFlow) {
                    Long transactionId = ((RemoveFlow) data).getTransactionId();
                    String switchId = ((RemoveFlow) data).getSwitchId();
                    String flowId = ((RemoveFlow) data).getId();

                    logger.debug("Flow remove message: {}={}, switch-id={}, flow-id={}, transaction-id={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), switchId, flowId, transactionId, request);

                    message.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(message), switchId, flowId, transactionId);
                    outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

                } else {
                    logger.warn("Skip undefined command message: {}={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), request);
                }
            } else if (message instanceof ErrorMessage) {
                String flowId = ((ErrorMessage) message).getData().getErrorDescription();
                FlowStatusType status = FlowStatusType.DOWN;

                logger.error("Flow error message: {}={}, flow-id={}, message={}",
                        CORRELATION_ID, message.getCorrelationId(), flowId, request);

                values = new Values(flowId, status);
                outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

            } else {
                logger.warn("Skip undefined message: {}={}, message={}",
                        CORRELATION_ID, message.getCorrelationId(), request);
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message={}", request, exception);
        } finally {
            logger.debug("Speaker request ack: message={}", request);
            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), fieldsMessageSwitchFlowTransaction);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), fieldsMessageSwitchFlowTransaction);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), fieldsFlowStatus);
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
