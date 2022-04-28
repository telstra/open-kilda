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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions;

import static java.lang.String.format;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.request.rulemanager.Origin;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
public class UpdateYFlowRulesAction extends
        FlowProcessingWithHistorySupportAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    protected final YFlowRepository yFlowRepository;
    protected final RuleManager ruleManager;

    public UpdateYFlowRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        this.ruleManager = ruleManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowPathSwapContext context,
                           FlowPathSwapFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Flow flow = getFlow(flowId);
        String yFlowId = flow.getYFlowId();
        if (yFlowId == null) {
            stateMachine.saveActionToHistory("No need to update y-flow rules - it's not a sub-flow");
            stateMachine.fire(Event.SKIP_YFLOW_RULES_UPDATE);
            return;
        }
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                        format("Y-flow %s not found in persistent storage", yFlowId)));

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        SwitchId sharedEndpoint = yFlow.getSharedEndpoint().getSwitchId();
        InstallSpeakerCommandsRequest installRequest = buildYFlowInstallRequest(sharedEndpoint,
                stateMachine.getNewPrimaryForwardPath(), stateMachine.getCommandContext());
        stateMachine.addInstallSpeakerCommand(installRequest.getCommandId(), installRequest);
        DeleteSpeakerCommandsRequest deleteRequest = buildYFlowDeleteRequest(sharedEndpoint,
                stateMachine.getOldPrimaryForwardPath(), stateMachine.getCommandContext());
        stateMachine.addDeleteSpeakerCommand(deleteRequest.getCommandId(), deleteRequest);

        // emitting
        Stream.of(installRequest, deleteRequest).forEach(command -> {
            stateMachine.getCarrier().sendSpeakerRequest(command);
            stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
        });

        stateMachine.saveActionToHistory("Commands for updating y-flow rules have been sent");
    }

    protected InstallSpeakerCommandsRequest buildYFlowInstallRequest(SwitchId switchId, PathId pathId,
                                                                     CommandContext context) {
        List<OfCommand> ofCommands = buildYFlowOfCommands(switchId, pathId);
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(),
                context.getCorrelationId());
        return new InstallSpeakerCommandsRequest(messageContext, switchId, commandId, ofCommands, Origin.FLOW_HS);
    }

    protected DeleteSpeakerCommandsRequest buildYFlowDeleteRequest(SwitchId switchId, PathId pathId,
                                                                   CommandContext context) {
        List<OfCommand> ofCommands = buildYFlowOfCommands(switchId, pathId);
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(),
                context.getCorrelationId());
        return new DeleteSpeakerCommandsRequest(messageContext, switchId, commandId, ofCommands, Origin.FLOW_HS);
    }

    private List<OfCommand> buildYFlowOfCommands(SwitchId switchId, PathId pathId) {
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(singleton(switchId))
                .pathIds(singleton(pathId))
                .build();
        List<SpeakerData> speakerData = ruleManager.buildRulesForSwitch(switchId, dataAdapter);
        return speakerData.stream()
                .filter(data -> data instanceof FlowSpeakerData)
                .map(data -> new FlowCommand((FlowSpeakerData) data))
                .collect(toList());
    }
}
