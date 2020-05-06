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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.action;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.neo4j.driver.v1.exceptions.ClientException;

@Slf4j
public class RevertPathsSwapAction extends FlowProcessingAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    private final int transactionRetriesLimit;

    public RevertPathsSwapAction(PersistenceManager persistenceManager, int transactionRetriesLimit) {
        super(persistenceManager);
        this.transactionRetriesLimit = transactionRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowPathSwapContext context,
                           FlowPathSwapFsm stateMachine) {
        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverablePersistenceException.class)
                .retryOn(ClientException.class)
                .withMaxRetries(transactionRetriesLimit);

        Flow f = persistenceManager.getTransactionManager().doInTransaction(retryPolicy, () -> {
            String flowId = stateMachine.getFlowId();
            Flow flow = getFlow(flowId);

            log.debug("Reverting primary and protected paths swap for flow {}", flowId);

            FlowPath oldPrimaryForward = flow.getForwardPath();
            FlowPath oldPrimaryReverse = flow.getReversePath();
            flow.setForwardPath(flow.getProtectedForwardPath());
            flow.setReversePath(flow.getProtectedReversePath());
            flow.setProtectedForwardPath(oldPrimaryForward);
            flow.setProtectedReversePath(oldPrimaryReverse);
            saveHistory(stateMachine, flow.getFlowId(), oldPrimaryForward.getPathId(), oldPrimaryReverse.getPathId());

            flowRepository.createOrUpdate(flow);
            return flow;
        });
    }

    private void saveHistory(FlowPathSwapFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        stateMachine.saveActionToHistory("Flow was reverted to old paths",
                format("The flow %s was updated with paths %s / %s", flowId, forwardPath, reversePath));
    }
}
