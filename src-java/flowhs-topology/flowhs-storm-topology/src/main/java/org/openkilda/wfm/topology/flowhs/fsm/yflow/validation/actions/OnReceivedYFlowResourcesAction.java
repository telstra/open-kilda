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

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class OnReceivedYFlowResourcesAction extends
        FlowProcessingAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {

    @Override
    public void perform(State from, State to, Event event, YFlowValidationContext context,
                        YFlowValidationFsm stateMachine) {
        MessageData data = context.getSpeakerResponse();
        if (data instanceof SwitchFlowEntries) {
            SwitchFlowEntries switchFlowEntries = (SwitchFlowEntries) data;
            log.info("Switch rules received for switch {}", switchFlowEntries.getSwitchId());
            stateMachine.getReceivedRules().add(switchFlowEntries);
            stateMachine.setAwaitingRules(stateMachine.getAwaitingRules() - 1);
            checkOfCompleteDataCollection(stateMachine);
        } else if (data instanceof SwitchMeterEntries) {
            SwitchMeterEntries switchMeterEntries = (SwitchMeterEntries) data;
            log.info("Switch meters received for switch {}", switchMeterEntries.getSwitchId());
            stateMachine.getReceivedMeters().add(switchMeterEntries);
            stateMachine.setAwaitingMeters(stateMachine.getAwaitingMeters() - 1);
            checkOfCompleteDataCollection(stateMachine);
        } else if (data instanceof SwitchMeterUnsupported) {
            SwitchMeterUnsupported meterUnsupported = (SwitchMeterUnsupported) data;
            log.info("Meters unsupported for switch {}", meterUnsupported.getSwitchId());
            stateMachine.getReceivedMeters().add(SwitchMeterEntries.builder()
                    .switchId(meterUnsupported.getSwitchId())
                    .meterEntries(Collections.emptyList())
                    .build());
            stateMachine.setAwaitingMeters(stateMachine.getAwaitingMeters() - 1);
            checkOfCompleteDataCollection(stateMachine);
        } else if (data instanceof ErrorData) {
            ErrorData errorData = (ErrorData) data;
            String errorMessage = format("%s : %s", errorData.getErrorMessage(), errorData.getErrorDescription());
            stateMachine.fireError(errorMessage);
        } else {
            String errorMessage = format("Unhandled message : %s", data);
            stateMachine.fireError(errorMessage);
        }
    }

    private void checkOfCompleteDataCollection(YFlowValidationFsm stateMachine) {
        if (stateMachine.getAwaitingRules() == 0 && stateMachine.getAwaitingMeters() == 0) {
            stateMachine.fire(Event.ALL_YFLOW_RESOURCES_RECEIVED);
        }
    }
}
