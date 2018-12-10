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
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.rule.FlowCommandErrorData;
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
    private static final Logger logger = LoggerFactory.getLogger(SpeakerBolt.class);

    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        logger.debug("Request tuple={}", tuple);

        String request = tuple.getString(0);

        try {
            Message message = MAPPER.readValue(request, Message.class);
            if (!Destination.WFM_TRANSACTION.equals(message.getDestination())) {
                return;
            }

            if (message instanceof CommandMessage) {
                CommandData data = ((CommandMessage) message).getData();
                if (data instanceof BaseFlow) {
                    logger.debug("Successful install/remove flow message: {}", message);
                    outputCollector.emit(StreamType.STATUS.toString(), tuple,
                            new Values(message, ((BaseFlow) data).getId()));
                } else {
                    logger.error("Skip undefined command message: {}", message);
                }
            } else if (message instanceof ErrorMessage) {
                ErrorData data = ((ErrorMessage) message).getData();
                if (data instanceof FlowCommandErrorData) {
                    logger.error("Flow error message: {}", message);
                    outputCollector.emit(StreamType.STATUS.toString(), tuple,
                            new Values(message, ((FlowCommandErrorData) data).getFlowId()));
                } else {
                    logger.error("Skip undefined error message: {}", message);
                }
            } else {
                logger.error("Skip undefined message: {}", message);
            }
        } catch (IOException e) {
            logger.error("Could not deserialize message: {}", request, e);
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        } finally {
            outputCollector.ack(tuple);

            logger.debug("Speaker message ack: {}", tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.STATUS.toString(), FlowTopology.fieldsMessageFlowId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
