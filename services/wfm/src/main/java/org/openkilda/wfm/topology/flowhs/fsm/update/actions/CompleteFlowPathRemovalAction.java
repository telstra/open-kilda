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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.neo4j.driver.v1.exceptions.ClientException;

import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class CompleteFlowPathRemovalAction extends
        BaseFlowPathRemovalAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final int transactionRetriesLimit;

    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager, int transactionRetriesLimit) {
        super(persistenceManager);
        this.transactionRetriesLimit = transactionRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverablePersistenceException.class)
                .retryOn(ClientException.class)
                .withMaxRetries(transactionRetriesLimit);

        persistenceManager.getTransactionManager().doInTransaction(retryPolicy, () -> removeFlowPaths(stateMachine));
    }

    private void removeFlowPaths(FlowUpdateFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());

        FlowPath oldPrimaryForward = null;
        FlowPath oldPrimaryReverse = null;
        if (stateMachine.getOldPrimaryForwardPath() != null && stateMachine.getOldPrimaryReversePath() != null) {
            oldPrimaryForward = getFlowPath(flow, stateMachine.getOldPrimaryForwardPath());
            oldPrimaryReverse = getFlowPath(flow, stateMachine.getOldPrimaryReversePath());
        }
        FlowPath oldProtectedForward = null;
        FlowPath oldProtectedReverse = null;
        if (stateMachine.getOldProtectedForwardPath() != null
                && stateMachine.getOldProtectedReversePath() != null) {
            oldProtectedForward = getFlowPath(flow, stateMachine.getOldProtectedForwardPath());
            oldProtectedReverse = getFlowPath(flow, stateMachine.getOldProtectedReversePath());
        }

        flowPathRepository.lockInvolvedSwitches(Stream.of(oldPrimaryForward, oldPrimaryReverse,
                oldProtectedForward, oldProtectedReverse).filter(Objects::nonNull).toArray(FlowPath[]::new));

        if (oldPrimaryForward != null && oldPrimaryReverse != null) {
            log.debug("Completing removal of the flow path {} / {}", oldPrimaryForward, oldPrimaryReverse);
            FlowPathPair pathsToDelete =
                    FlowPathPair.builder().forward(oldPrimaryForward).reverse(oldPrimaryReverse).build();
            deleteFlowPaths(pathsToDelete);
            saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
        }

        if (oldProtectedForward != null && oldProtectedReverse != null) {
            log.debug("Completing removal of the flow path {} / {}", oldProtectedForward, oldProtectedReverse);
            FlowPathPair pathsToDelete =
                    FlowPathPair.builder().forward(oldProtectedForward).reverse(oldProtectedReverse).build();
            deleteFlowPaths(pathsToDelete);
            saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
        }
    }
}
