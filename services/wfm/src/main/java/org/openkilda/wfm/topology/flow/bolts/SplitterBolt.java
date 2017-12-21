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
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowGetRequest;
import org.openkilda.messaging.command.flow.FlowPathRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowRestoreRequest;
import org.openkilda.messaging.command.flow.FlowStatusRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsGetRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

/**
 * Northbound Request Bolt. Handles northbound requests.
 */
public class SplitterBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(SplitterBolt.class);

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * Tries the parse the json object and return a null if can't
     *
     * @param json the json to parse
     * @return an InfoMessage, if possible; otherwise null
     */
    private Message tryMessage(String json){
        Message result = null;
        try {
            result = MAPPER.readValue(json, Message.class);
        } catch (Exception e){
            /* do nothing */
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        String request = tuple.getString(0);
        Values values = new Values(request);

        try {
            Message message =  tryMessage(request);
            if (message == null
                    || !Destination.WFM.equals(message.getDestination())
                    || !(message instanceof CommandMessage)) {
                return;
            }

            logger.debug("Request tuple={}", tuple);
            CommandData data = ((CommandMessage) message).getData();

            if (data instanceof FlowCreateRequest) {
                String flowId = ((FlowCreateRequest) data).getPayload().getFlowId();

                logger.info("Flow {} create message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.CREATE.toString(), tuple, values);

            } else if (data instanceof FlowDeleteRequest) {
                String flowId = ((FlowDeleteRequest) data).getPayload().getFlowId();

                logger.info("Flow {} delete message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.DELETE.toString(), tuple, values);

            } else if (data instanceof FlowUpdateRequest) {
                String flowId = ((FlowUpdateRequest) data).getPayload().getFlowId();

                logger.info("Flow {} update message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.UPDATE.toString(), tuple, values);

            } else if (data instanceof FlowRestoreRequest) {
                String flowId = ((FlowRestoreRequest) data).getPayload().getLeft().getFlowId();

                logger.info("Flow {} restore message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.RESTORE.toString(), tuple, values);

            } else if (data instanceof FlowRerouteRequest) {
                String flowId = ((FlowRerouteRequest) data).getPayload().getFlowId();

                logger.info("Flow {} reroute message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.REROUTE.toString(), tuple, values);

            } else if (data instanceof FlowStatusRequest) {
                String flowId = ((FlowStatusRequest) data).getPayload().getId();

                logger.info("Flow {} status message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.STATUS.toString(), tuple, values);

            } else if (data instanceof FlowGetRequest) {
                String flowId = ((FlowGetRequest) data).getPayload().getId();

                logger.info("Flow {} get message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.READ.toString(), tuple, values);

            } else if (data instanceof FlowsGetRequest) {
                logger.info("Flows get message: values={}", values);

                values = new Values(message, null);
                outputCollector.emit(StreamType.READ.toString(), tuple, values);

            } else if (data instanceof FlowPathRequest) {
                String flowId = ((FlowPathRequest) data).getPayload().getId();

                logger.info("Flow {} path message: values={}", flowId, values);

                values = new Values(message, flowId);
                outputCollector.emit(StreamType.PATH.toString(), tuple, values);

            } else {
                logger.debug("Skip undefined message: {}={}", Utils.CORRELATION_ID, message.getCorrelationId());
            }

/*
 * (crimi) This was commented out since the parsing of the message is handled in tryMessage.
 * Due to refactoring the kafka topics, it appears more messages are coming to the splitter than
 * originally desinged for.
 *
 * TODO: Fix the cause of excess messages coming to the splitter.
 */
//

//        } catch (IOException exception) {
//            String message = String.format("Could not deserialize message: %s", request);
//            logger.error("{}", message, exception);
//
//            ErrorMessage errorMessage = new ErrorMessage(
//                    new ErrorData(ErrorType.REQUEST_INVALID, message, exception.getMessage()),
//                    System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID, Destination.NORTHBOUND);
//
//            values = new Values(errorMessage, ErrorType.INTERNAL_ERROR);
//            outputCollector.emit(StreamType.ERROR.toString(), tuple, values);

        } finally {
            logger.debug("Splitter message ack: component={}, stream={}, tuple={}, values={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple, values);


            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.READ.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.UPDATE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.PATH.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.RESTORE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.REROUTE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.ERROR.toString(), FlowTopology.fieldsMessageErrorType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
