/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.bolt;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_REMOVE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_UPDATE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowSplitterBolt.COMMAND_DATA_FIELD;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.ComponentId;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowStateCacheService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowStateCacheBolt extends AbstractBolt {
    private transient FlowStateCacheService flowStateCacheService;

    public FlowStateCacheBolt(PersistenceManager persistenceManager, String lifeCycleEventSourceComponent) {
        super(persistenceManager, lifeCycleEventSourceComponent);
    }

    @PersistenceContextRequired(requiresNew = true)
    protected void init() {
        flowStateCacheService = new FlowStateCacheService(persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (active) {
            if (ComponentId.TICK_BOLT.name().equals(input.getSourceComponent())) {
                flowStateCacheService.getFlows()
                        .forEach(flowId -> emit(input, new Values(flowId, getCommandContext())));
                return;
            }

            if (FLOW_UPDATE_STREAM_ID.name().equals(input.getSourceStreamId())) {
                CommandData payload = pullValue(input, COMMAND_DATA_FIELD, CommandData.class);
                if (payload instanceof UpdateFlowCommand) {
                    UpdateFlowCommand updateFlowCommand = (UpdateFlowCommand) payload;
                    flowStateCacheService.updateFlow(updateFlowCommand);
                    emit(FLOW_UPDATE_STREAM_ID.name(), input, new Values(updateFlowCommand.getFlowId(),
                            updateFlowCommand, getCommandContext()));
                } else {
                    unhandledInput(input);
                }
                return;
            }
            if (FLOW_REMOVE_STREAM_ID.name().equals(input.getSourceStreamId())) {
                String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
                flowStateCacheService.removeFlow(flowId);
                emit(FLOW_REMOVE_STREAM_ID.name(), input, new Values(flowId, getCommandContext()));
            } else {
                unhandledInput(input);
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FLOW_ID_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_UPDATE_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, COMMAND_DATA_FIELD,
                FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_REMOVE_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
