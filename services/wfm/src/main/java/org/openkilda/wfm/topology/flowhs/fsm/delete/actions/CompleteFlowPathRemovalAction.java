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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.neo4j.driver.v1.exceptions.ClientException;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class CompleteFlowPathRemovalAction extends
        BaseFlowPathRemovalAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    private final int transactionRetriesLimit;

    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager, int transactionRetriesLimit) {
        super(persistenceManager);
        this.transactionRetriesLimit = transactionRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverablePersistenceException.class)
                .retryOn(ClientException.class)
                .withMaxRetries(transactionRetriesLimit);

        persistenceManager.getTransactionManager().doInTransaction(retryPolicy, () -> removeFlowPaths(stateMachine));
    }

    private void removeFlowPaths(FlowDeleteFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        FlowPath[] paths = flow.getPaths().stream().filter(Objects::nonNull).toArray(FlowPath[]::new);

        flowPathRepository.lockInvolvedSwitches(paths);

        Set<PathId> processed = new HashSet<>();
        for (FlowPath path : paths) {
            PathId pathId = path.getPathId();
            if (processed.add(pathId)) {
                FlowPath oppositePath = flow.getOppositePathId(pathId)
                        .filter(oppPathId -> !pathId.equals(oppPathId)).flatMap(flow::getPath).orElse(null);
                if (oppositePath != null && processed.add(oppositePath.getPathId())) {
                    log.debug("Removing the flow paths {} / {}", pathId, oppositePath.getPathId());
                    FlowPath forward = path.isForward() ? path : oppositePath;
                    FlowPath reverse = path.isForward() ? oppositePath : path;
                    FlowPathPair pathsToDelete =
                            FlowPathPair.builder().forward(forward).reverse(reverse).build();
                    deleteFlowPaths(pathsToDelete);

                    saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
                } else {
                    log.debug("Removing the flow path {}", pathId);
                    deleteFlowPath(path);
                    saveRemovalActionWithDumpToHistory(stateMachine, flow, path);
                }
            }
        }
    }
}
