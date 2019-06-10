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
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.StreamType;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Northbound Reply Bolt. Forms northbound replies.
 */
public class NorthboundReplyBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NorthboundReplyBolt.class);

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        String streamId = tuple.getSourceStreamId();
        Message message = (Message) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
        Values values = null;

        try {
            logger.debug("Request tuple={}", tuple);

            switch (componentId) {

                case CRUD_BOLT:
                case ERROR_BOLT:
                case FLOW_OPERATION_BOLT:
                    logger.debug("Flow response: {}={}, component={}, stream={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);

                    message.setDestination(Destination.NORTHBOUND);
                    values = new Values(MAPPER.writeValueAsString(message));
                    outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);

                    break;

                default:
                    logger.debug("Flow UNKNOWN response: {}={}, component={}, stream={}, message={}",
                            Utils.CORRELATION_ID, message.getCorrelationId(), componentId, streamId, message);
                    break;
            }
        } catch (JsonProcessingException exception) {
            logger.error("Could not serialize message: component={}, stream={}, message={}",
                    componentId, streamId, message);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);

            logger.debug("Northbound-Reply message ack: component={}, stream={}, tuple={}, values={}",
                    tuple.getSourceComponent(), tuple.getSourceStreamId(), tuple, values);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), AbstractTopology.fieldMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}

