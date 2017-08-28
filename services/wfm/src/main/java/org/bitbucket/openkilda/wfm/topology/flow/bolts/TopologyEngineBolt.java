package org.bitbucket.openkilda.wfm.topology.flow.bolts;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.messaging.Utils.TRANSACTION_ID;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldMessage;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageFlowId;
import static org.bitbucket.openkilda.wfm.topology.flow.FlowTopology.fieldsMessageSwitchIdFlowIdTransactionId;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.BaseInstallFlow;
import org.bitbucket.openkilda.messaging.command.flow.RemoveFlow;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
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
import java.util.UUID;

/**
 * Topology-Engine Bolt. Processes replies from Topology-Engine service.
 */
public class TopologyEngineBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(TopologyEngineBolt.class);

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
            if (!Destination.WFM.equals(message.getDestination())) {
                return;
            }

            if (message instanceof CommandMessage) {
                CommandData data = ((CommandMessage) message).getData();

                if (data instanceof BaseInstallFlow) {
                    BaseInstallFlow installData = (BaseInstallFlow) data;
                    Long transactionId = UUID.randomUUID().getLeastSignificantBits();
                    installData.setTransactionId(transactionId);
                    String switchId = installData.getSwitchId();
                    String flowId = installData.getId();

                    logger.debug("Flow install message: {}={}, switch-id={}, {}={}, {}={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), switchId,
                            Utils.FLOW_ID, flowId, TRANSACTION_ID, transactionId, request);

                    message.setDestination(Destination.CONTROLLER);
                    values = new Values(MAPPER.writeValueAsString(message), switchId, flowId, transactionId);
                    outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

                } else if (data instanceof RemoveFlow) {
                    RemoveFlow removeData = (RemoveFlow) data;
                    Long transactionId = UUID.randomUUID().getLeastSignificantBits();
                    removeData.setTransactionId(transactionId);
                    String switchId = removeData.getSwitchId();
                    String flowId = removeData.getId();

                    logger.debug("Flow remove message: {}={}, switch-id={}, {}={}, {}={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), switchId,
                            Utils.FLOW_ID, flowId, TRANSACTION_ID, transactionId, request);

                    message.setDestination(Destination.CONTROLLER);
                    values = new Values(MAPPER.writeValueAsString(message), switchId, flowId, transactionId);
                    outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

                } else {
                    logger.warn("Skip undefined command message: {}={}, message={}",
                            CORRELATION_ID, message.getCorrelationId(), request);
                }
            } else if (message instanceof InfoMessage) {
                values = new Values(message);

                logger.debug("Flow response message: {}={}, message={}",
                        CORRELATION_ID, message.getCorrelationId(), request);

                outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);

            } else if (message instanceof ErrorMessage) {
                String flowId = ((ErrorMessage) message).getData().getErrorDescription();

                logger.error("Flow error message: {}={}, {}={}, message={}",
                        CORRELATION_ID, message.getCorrelationId(), Utils.FLOW_ID, flowId, request);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

            } else {
                logger.warn("Skip undefined message: {}={}, message={}",
                        CORRELATION_ID, message.getCorrelationId(), request);
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message={}", request, exception);
        } finally {
            logger.debug("Topology message ack: message={}", request);

            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), fieldsMessageSwitchIdFlowIdTransactionId);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), fieldsMessageSwitchIdFlowIdTransactionId);
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), fieldsMessageFlowId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
