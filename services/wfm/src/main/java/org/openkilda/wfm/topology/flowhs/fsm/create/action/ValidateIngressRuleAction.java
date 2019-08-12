/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.model.SwitchFeatures;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.rules.IngressRulesValidator;
import org.openkilda.wfm.topology.flowhs.validation.rules.RulesValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class ValidateIngressRuleAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final SwitchFeaturesRepository switchFeaturesRepository;

    public ValidateIngressRuleAction(PersistenceManager persistenceManager) {
        super(persistenceManager);

        this.switchFeaturesRepository = persistenceManager.getRepositoryFactory().createSwitchFeaturesRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        UUID commandId = context.getSpeakerFlowResponse().getCommandId();

        InstallIngressRule expected = stateMachine.getIngressCommands().get(commandId);
        SwitchFeatures switchFeatures =  switchFeaturesRepository.findBySwitchId(expected.getSwitchId())
                .orElseThrow(() -> new IllegalStateException(format("Failed to find list of features for switch %s",
                        expected.getSwitchId())));

        FlowRuleResponse response = (FlowRuleResponse) context.getSpeakerFlowResponse();
        RulesValidator validator = new IngressRulesValidator(expected, response, switchFeatures);
        String action;
        if (!validator.validate()) {
            stateMachine.getFailedCommands().add(commandId);
            action = format("Rule is valid: switch %s, cookie %s", expected.getSwitchId(), expected.getCookie());
        } else {
            action = format("Rule is invalid: switch %s, cookie %s",  expected.getSwitchId(), expected.getCookie());
        }

        stateMachine.getPendingCommands().remove(commandId);
        if (stateMachine.getPendingCommands().isEmpty()) {
            log.debug("Ingress rules have been validated for flow {}", stateMachine.getFlowId());
            saveHistory(stateMachine, expected, action);
            stateMachine.fire(Event.NEXT);
        }
    }

    private void saveHistory(FlowCreateFsm stateMachine, InstallIngressRule expected, String action) {
        String description = format("Ingress rule validation is completed: switch %s, cookie %s",
                expected.getSwitchId().toString(), expected.getCookie());
        saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(), action, description);
    }
}
