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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.model.FlowPathSnapshot;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowProcessingAction<T extends FlowProcessingFsm<T, S, E, C>, S, E, C>
        extends AnonymousAction<T, S, E, C> {

    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    protected final PersistenceManager persistenceManager;
    protected final FlowRepository flowRepository;
    protected final FlowPathRepository flowPathRepository;

    public FlowProcessingAction(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
    }

    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        try {
            perform(from, to, event, context, stateMachine);
        } catch (Exception ex) {
            String errorMessage = format("%s failed: %s", getClass().getSimpleName(), ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);
        }
    }

    protected abstract void perform(S from, S to, E event, C context, T stateMachine);

    protected Flow getFlow(String flowId) {
        return flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    protected Flow getFlow(String flowId, FetchStrategy fetchStrategy) {
        return flowRepository.findById(flowId, fetchStrategy)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    protected FlowPathSnapshot getFlowPath(FlowPathSnapshot pathSnapshot) {
        return pathSnapshot.toBuilder()
                .path(getFlowPath(pathSnapshot.getPath().getPathId()))
                .build();
    }

    protected FlowPathSnapshot getFlowPath(Flow flow, FlowPathSnapshot pathSnapshot) {
        return pathSnapshot.toBuilder()
                .path(getFlowPath(flow, pathSnapshot.getPath().getPathId()))
                .build();
    }

    protected FlowPath getFlowPath(Flow flow, PathId pathId) {
        return flow.getPath(pathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow path %s not found", pathId)));
    }

    protected FlowPath getFlowPath(PathId pathId) {
        return flowPathRepository.findById(pathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow path %s not found", pathId)));
    }

    protected boolean isRemoveCustomerPortSharedCatchRule(String flowId,
                                                          SwitchId ingressSwitchId, int ingressPort) {
        Set<String> flowIds = flowRepository.findByEndpointWithMultiTableSupport(ingressSwitchId, ingressPort)
                .stream()
                .map(Flow::getFlowId)
                .collect(Collectors.toSet());

        return flowIds.size() == 1 && flowIds.iterator().next().equals(flowId);
    }
}
