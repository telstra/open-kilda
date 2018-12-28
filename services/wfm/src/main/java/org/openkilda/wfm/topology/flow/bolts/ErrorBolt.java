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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
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

import java.util.Map;

/**
 * Error Bolt. Processes error messages and forms Northbound responses.
 */
public class ErrorBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ErrorBolt.class);

    /**
     * Output collector.
     */
    private transient OutputCollector outputCollector;

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
                    logger.debug("Skip message from UNKNOWN component: component={}, stream={}, error-type={}",
                            componentId, streamId, errorType);
                    break;
            }
        } catch (Exception exception) {
            logger.error("Could not process message: {}", tuple, exception);
        } finally {
            outputCollector.ack(tuple);

            logger.debug("Error message ack: component={}, stream={}, tuple={}, values={}",
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
}
