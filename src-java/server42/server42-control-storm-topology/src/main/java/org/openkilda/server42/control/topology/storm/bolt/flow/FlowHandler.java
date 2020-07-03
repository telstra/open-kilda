/* Copyright 2020 Telstra Open Source
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

package org.openkilda.server42.control.topology.storm.bolt.flow;

import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.server42.control.messaging.flowrtt.AddFlow;
import org.openkilda.server42.control.messaging.flowrtt.ClearFlows;
import org.openkilda.server42.control.messaging.flowrtt.Headers;
import org.openkilda.server42.control.messaging.flowrtt.RemoveFlow;
import org.openkilda.server42.control.topology.service.FlowRttService;
import org.openkilda.server42.control.topology.service.IFlowCarrier;
import org.openkilda.server42.control.topology.storm.ComponentId;
import org.openkilda.server42.control.topology.storm.bolt.flow.command.FlowCommand;
import org.openkilda.server42.control.topology.storm.bolt.router.Router;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowHandler extends AbstractBolt
        implements IFlowCarrier {

    public static final String BOLT_ID = ComponentId.FLOW_HANDLER.toString();

    public static final String STREAM_CONTROL_COMMANDS_ID = "control.commands";
    public static final Fields STREAM_CONTROL_COMMANDS_FIELDS = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
            FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
    private final PersistenceManager persistenceManager;
    private transient FlowRttService flowRttService;

    public FlowHandler(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    protected void init() {
        this.flowRttService = new FlowRttService(this, persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (Router.BOLT_ID.equals(source)) {
            handleRouterCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(STREAM_CONTROL_COMMANDS_ID, STREAM_CONTROL_COMMANDS_FIELDS);
    }

    private void handleRouterCommand(Tuple input) throws PipelineException {
        handleCommand(input, Router.FIELD_ID_COMMAND);
    }

    private void handleCommand(Tuple input, String fieldName) throws PipelineException {
        FlowCommand command = pullValue(input, fieldName, FlowCommand.class);
        command.apply(this);
    }

    public void processActivateFlowMonitoring(String flowId, FlowEndpointPayload flow, boolean isForward) {
        flowRttService.activateFlowMonitoring(flowId, flow.getDatapath(), flow.getPortNumber(),
                flow.getVlanId(), isForward);
    }

    public void processDeactivateFlowMonitoring(SwitchId switchId, String flowId) {
        // no logic just repack
        notifyDeactivateFlowMonitoring(switchId, flowId);
    }

    public void processActivateFlowMonitoringOnSwitch(SwitchId switchId) {
        flowRttService.activateFlowMonitoringForSwitch(switchId);
    }

    public void processDeactivateFlowMonitoringOnSwitch(SwitchId switchId) {
        // no logic just repack
        notifyDeactivateFlowMonitoring(switchId);
    }

    @Override
    public void notifyActivateFlowMonitoring(String flowId, SwitchId switchId, Integer port, Integer vlan,
                                             boolean isForward) {
        AddFlow addFlow = AddFlow.builder()
                .flowId(flowId)
                .port(port)
                .tunnelId(vlan.longValue())
                .direction(isForward ? FlowDirection.FORWARD : FlowDirection.REVERSE)
                .headers(buildHeader())
                .build();

        emit(STREAM_CONTROL_COMMANDS_ID, getCurrentTuple(), new Values(switchId.toString(), addFlow));
    }

    @Override
    public void notifyDeactivateFlowMonitoring(SwitchId switchId, String flowId) {
        RemoveFlow removeFlow = RemoveFlow.builder()
                .flowId(flowId)
                .headers(buildHeader())
                .build();
        emit(STREAM_CONTROL_COMMANDS_ID, getCurrentTuple(), new Values(switchId.toString(), removeFlow));
    }

    private void notifyDeactivateFlowMonitoring(SwitchId switchId) {
        ClearFlows clearFlows = ClearFlows.builder()
                .headers(buildHeader())
                .build();
        emit(STREAM_CONTROL_COMMANDS_ID, getCurrentTuple(), new Values(switchId.toString(), clearFlows));
    }

    private Headers buildHeader() {
        return Headers.builder().correlationId(getCommandContext().getCorrelationId()).build();
    }
}
