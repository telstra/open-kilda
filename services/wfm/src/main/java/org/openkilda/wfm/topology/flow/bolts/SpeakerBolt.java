/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.flow.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Speaker Bolt. Processes replies from OpenFlow Speaker service.
 */
public class SpeakerBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(SpeakerBolt.class);

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        String request = tuple.getString(0);
        Values values = null;

        try {

            Message message = MAPPER.readValue(request, Message.class);
            logger.debug("Request tuple={}", tuple);

            if (!Destination.WFM_TRANSACTION.equals(message.getDestination())) {
                return;
            }

            if (message instanceof CommandMessage) {

                CommandData data = ((CommandMessage) message).getData();

                if (data instanceof BaseInstallFlow) {
                    Long transactionId = ((BaseInstallFlow) data).getTransactionId();
                    String switchId = ((BaseInstallFlow) data).getSwitchId();
                    String flowId = ((BaseInstallFlow) data).getId();

                    logger.debug("Flow install message: {}={}, switch-id={}, {}={}, {}={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), switchId,
                            Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId, request);

                    message.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(message), switchId, flowId, transactionId);
                    // FIXME(surabujin): looks like TE ignore this messages
                    outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

                } else if (data instanceof RemoveFlow) {

                    Long transactionId = ((RemoveFlow) data).getTransactionId();
                    String switchId = ((RemoveFlow) data).getSwitchId();
                    String flowId = ((RemoveFlow) data).getId();

                    logger.debug("Flow remove message: {}={}, switch-id={}, {}={}, {}={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), switchId,
                            Utils.FLOW_ID, flowId, Utils.TRANSACTION_ID, transactionId, request);

                    message.setDestination(Destination.TOPOLOGY_ENGINE);
                    values = new Values(MAPPER.writeValueAsString(message), switchId, flowId, transactionId);
                    outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

                } else {
                    logger.debug("Skip undefined command message: {}={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), request);
                }
            } else if (message instanceof ErrorMessage) {
                String flowId = ((ErrorMessage) message).getData().getErrorDescription();
                FlowState status = FlowState.DOWN;

                // TODO: Should add debug message if receiving ErrorMessage.
                if (flowId != null) {
                    logger.error("Flow error message: {}={}, {}={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), Utils.FLOW_ID, flowId, request);

                    values = new Values(flowId, status);
                    outputCollector.emit(StreamType.STATUS.toString(), tuple, values);
                } else {
                    logger.debug("Skip error message without flow-id: {}={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), request);
                }

            } else {
                // TODO: should this be a warn or error? Probably, after refactored / specific
                // topics
                logger.debug("Skip undefined message: {}={}, message={}",
                        Utils.CORRELATION_ID, message.getCorrelationId(), request);
            }
        } catch (IOException exception) {
            logger.error("\n\nCould not deserialize message={}", request, exception);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);

            logger.debug("Speaker message ack: component={}, stream={}, tuple={}, values={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple, values);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(
                StreamType.CREATE.toString(), FlowTopology.fieldsMessageSwitchIdFlowIdTransactionId);
        outputFieldsDeclarer.declareStream(
                StreamType.DELETE.toString(), FlowTopology.fieldsMessageSwitchIdFlowIdTransactionId);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), FlowTopology.fieldsFlowIdStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
