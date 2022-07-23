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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidateSubFlowsAction extends
        YFlowProcessingAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {
    private final FlowValidationHubService flowValidationHubService;

    public ValidateSubFlowsAction(PersistenceManager persistenceManager,
                                  FlowValidationHubService flowValidationHubService) {
        super(persistenceManager);
        this.flowValidationHubService = flowValidationHubService;
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowValidationContext context,
                           YFlowValidationFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Y-flow %s not found", yFlowId)));
        if (yFlow.getStatus() == FlowStatus.DOWN) {
            throw new FlowProcessingException(ErrorType.UNPROCESSABLE_REQUEST,
                    format("Could not validate y-flow: y-flow %s in in DOWN state", yFlowId));
        }

        log.debug("Start validating {} sub-flows of y-flow {}", yFlow.getSubFlows().size(), yFlowId);
        stateMachine.clearValidatingSubFlows();

        yFlow.getSubFlows().forEach(subFlow -> {
            String subFlowId = subFlow.getSubFlowId();
            stateMachine.addSubFlow(subFlowId);
            stateMachine.addValidatingSubFlow(subFlowId);
            stateMachine.notifyEventListeners(listener ->
                    listener.onSubFlowProcessingStart(yFlowId, subFlowId));
            CommandContext flowContext = stateMachine.getCommandContext().fork(subFlowId);
            flowValidationHubService.startFlowValidation(flowContext, subFlowId);
        });
    }
}
