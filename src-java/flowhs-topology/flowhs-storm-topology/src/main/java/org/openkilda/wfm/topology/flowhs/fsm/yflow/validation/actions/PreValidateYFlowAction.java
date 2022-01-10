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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PreValidateYFlowAction extends
        NbTrackableAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {
    private final YFlowRepository yFlowRepository;

    public PreValidateYFlowAction(PersistenceManager persistenceManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    public Optional<Message> performWithResponse(State from, State to, Event event, YFlowValidationContext context,
                                                 YFlowValidationFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Y-flow %s not found", yFlowId)));
        if (yFlow.getStatus() == FlowStatus.IN_PROGRESS || yFlow.getStatus() == FlowStatus.DOWN) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    format("Y-flow %s is in progress or down now", yFlowId));
        }
        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not validate y-flow";
    }
}
