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
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
public class RemoveFlowAction extends FlowProcessingAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    private final int transactionRetriesLimit;

    public RemoveFlowAction(PersistenceManager persistenceManager, int transactionRetriesLimit) {
        super(persistenceManager);
        this.transactionRetriesLimit = transactionRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverablePersistenceException.class)
                .withMaxRetries(transactionRetriesLimit);

        persistenceManager.getTransactionManager().doInTransaction(retryPolicy, () -> {
            Flow flow = getFlow(stateMachine.getFlowId());
            log.debug("Removing the flow {}", flow);
            flowRepository.remove(flow);

            stateMachine.saveActionToHistory("Flow was removed",
                    String.format("The flow %s was removed", stateMachine.getFlowId()));
        });
    }
}
