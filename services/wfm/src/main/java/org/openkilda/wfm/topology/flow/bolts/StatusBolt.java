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
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.service.BaseFlowService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class StatusBolt extends AbstractBolt {
    private static final Logger logger = LoggerFactory.getLogger(StatusBolt.class);

    private final PersistenceManager persistenceManager;

    private transient BaseFlowService flowService;

    public StatusBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        flowService = new BaseFlowService(persistenceManager);
        super.prepare(stormConf, context, collector);
    }

    @Override
    public void handleInput(Tuple tuple) {
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        try {
            switch (streamId) {
                case STATUS:
                    String flowId = tuple.getStringByField(Utils.FLOW_ID);
                    FlowState state = (FlowState) tuple.getValueByField(FlowTopology.FLOW_STATUS_FIELD);
                    logger.info("Set status {} for: {}={}", state, Utils.FLOW_ID, flowId);
                    flowService.updateFlowStatus(flowId, FlowMapper.INSTANCE.map(state));
                    break;
                default:
                    logger.error("Unexpected stream: {} in {}", streamId, tuple);
                    break;
            }

        } catch (Exception e) {
            logger.error("Failed to update status for: {}", tuple, e);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        // The bolt doesn't emit anything.
    }
}
