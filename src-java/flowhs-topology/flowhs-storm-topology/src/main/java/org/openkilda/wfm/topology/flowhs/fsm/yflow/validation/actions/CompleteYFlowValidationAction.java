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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowDiscrepancyDto;
import org.openkilda.messaging.command.yflow.YFlowValidationResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationService;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CompleteYFlowValidationAction extends
        NbTrackableAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {
    private YFlowValidationService validationService;

    public CompleteYFlowValidationAction(YFlowValidationService validationService) {
        this.validationService = validationService;
    }

    @Override
    public Optional<Message> performWithResponse(State from, State to, Event event, YFlowValidationContext context,
                                                 YFlowValidationFsm stateMachine)
            throws FlowNotFoundException, SwitchNotFoundException {
        YFlowDiscrepancyDto resourcesValidationResult =
                validationService.validateYFlowResources(stateMachine.getYFlowId(), stateMachine.getReceivedRules(),
                        stateMachine.getReceivedMeters());

        YFlowValidationResponse result = new YFlowValidationResponse();
        result.setYFlowValidationResult(resourcesValidationResult);
        result.setSubFlowValidationResults(stateMachine.getSubFlowValidationResults());

        boolean notAsExpected = !resourcesValidationResult.isAsExpected()
                || stateMachine.getSubFlowValidationResults().stream()
                .map(FlowValidationResponse::getAsExpected)
                .anyMatch(n -> !n);
        result.setAsExpected(!notAsExpected);

        CommandContext commandContext = stateMachine.getCommandContext();
        InfoMessage message = new InfoMessage(result, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
        return Optional.of(message);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not validate flow";
    }
}
