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

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnSubFlowValidatedAction extends
        FlowProcessingAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {
    @Override
    protected void perform(State from, State to, Event event, YFlowValidationContext context,
                           YFlowValidationFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isValidatingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.addSubFlowValidationResults(context.getValidationResult());
        stateMachine.removeValidatingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        if (stateMachine.getValidatingSubFlows().isEmpty()) {
            if (stateMachine.getFailedSubFlows().isEmpty()) {
                stateMachine.fire(Event.ALL_SUB_FLOWS_VALIDATED);
            } else {
                stateMachine.fire(Event.FAILED_TO_VALIDATE_SUB_FLOWS);
            }
        }
    }
}
