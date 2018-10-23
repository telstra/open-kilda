/* Copyright 2018 Telstra Open Source
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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.converter.FlowMapper;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.service.FlowService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class StatusBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(StatusBolt.class);

    private static final FlowMapper FLOW_MAPPER = Mappers.getMapper(FlowMapper.class);


    private transient FlowService flowService;

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    private PersistenceManager neo4jPersistenceManager;

    public StatusBolt(PersistenceManager neo4jPersistenceManager) {
        this.neo4jPersistenceManager = neo4jPersistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        outputCollector = collector;
        flowService = new FlowService(neo4jPersistenceManager);

    }

    @Override
    public void execute(Tuple tuple) {
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        try {
            switch (streamId) {
                case STATUS:
                    FlowState state = (FlowState) tuple.getValueByField(FlowTopology.STATUS_FIELD);
                    String flowId = tuple.getStringByField(Utils.FLOW_ID);
                    String correlationId = Utils.DEFAULT_CORRELATION_ID;

                    logger.info("State flow: {}={}", flowId, state);
                    FlowPair<Flow, Flow> flow = FLOW_MAPPER.flowPairToDto(
                            flowService.updateFlowStatus(flowId, FLOW_MAPPER.flowStateFromDto(state)));
                    break;
                default:
                    logger.debug("Unexpected stream: component={}, stream={}", componentId, streamId);
                    break;
            }

        } catch (Exception e) {
            logger.error("Failed to update status for: component={}, stream={}", componentId, streamId);
        } finally {
            outputCollector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
    }
}
