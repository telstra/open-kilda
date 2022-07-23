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

import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowValidationHubCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DumpYFlowResourcesAction extends
        YFlowProcessingAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {
    private final YFlowRepository yFlowRepository;

    public DumpYFlowResourcesAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowValidationContext context,
                           YFlowValidationFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Y-flow %s not found", yFlowId)));
        log.debug("Start validating y-flow {} resources", yFlowId);

        List<SwitchId> switchIds = Stream.of(yFlow.getSharedEndpoint().getSwitchId(), yFlow.getYPoint(),
                yFlow.getProtectedPathYPoint()).filter(Objects::nonNull).collect(Collectors.toList());

        int switchCount = switchIds.size();
        stateMachine.setAwaitingRules(switchCount);
        stateMachine.setAwaitingMeters(switchCount);
        log.debug("Send commands to get rules & meters on {} switches", switchCount);
        YFlowValidationHubCarrier carrier = stateMachine.getCarrier();
        switchIds.forEach(switchId -> {
            carrier.sendSpeakerRequest(yFlowId, new DumpRulesForFlowHsRequest(switchId));
            carrier.sendSpeakerRequest(yFlowId, new DumpMetersForFlowHsRequest(switchId));
        });
    }
}
