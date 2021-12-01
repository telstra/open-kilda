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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CreateDraftYFlowAction extends NbTrackableAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final YFlowRepository yFlowRepository;

    public CreateDraftYFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowCreateContext context,
                                                    YFlowCreateFsm stateMachine) {
        YFlowRequest targetFlow = stateMachine.getTargetFlow();
        String yFlowId = targetFlow.getYFlowId();
        if (yFlowRepository.exists(yFlowId)) {
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS,
                    format("Y-flow %s already exists", yFlowId));
        }

        YFlow result = transactionManager.doInTransaction(() -> {
            YFlow yFlow = YFlowRequestMapper.INSTANCE.toYFlow(targetFlow);
            yFlow.setStatus(FlowStatus.IN_PROGRESS);
            yFlowRepository.add(yFlow);
            return yFlow;
        });

        stateMachine.saveActionToHistory(format("Draft y-flow was created with status %s", result.getStatus()));

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create y-flow";
    }
}
