/* Copyright 2020 Telstra Open Source
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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.neo4j.driver.v1.exceptions.ClientException;

import java.util.Optional;

@Slf4j
public class UpdateFlowPathsAction extends NbTrackableAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    private final int transactionRetriesLimit;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;

    public UpdateFlowPathsAction(PersistenceManager persistenceManager, int transactionRetriesLimit) {
        super(persistenceManager);
        this.transactionRetriesLimit = transactionRetriesLimit;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        islRepository = repositoryFactory.createIslRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowPathSwapContext context,
                                                    FlowPathSwapFsm stateMachine) {

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverablePersistenceException.class)
                .retryOn(ClientException.class)
                .withMaxRetries(transactionRetriesLimit);

        Flow f = persistenceManager.getTransactionManager().doInTransaction(retryPolicy, () -> {
            String flowId = stateMachine.getFlowId();
            Flow flow = getFlow(flowId);

            log.debug("Swapping primary and protected paths for flow {}", flowId);

            FlowPath oldPrimaryForward = flow.getForwardPath();
            FlowPath oldPrimaryReverse = flow.getReversePath();
            flow.setForwardPath(flow.getProtectedForwardPath());
            flow.setReversePath(flow.getProtectedReversePath());
            flow.setProtectedForwardPath(oldPrimaryForward);
            flow.setProtectedReversePath(oldPrimaryReverse);
            flowRepository.createOrUpdate(flow);
            return flow;
        });

        stateMachine.setNewPrimaryForwardPath(f.getForwardPathId());
        stateMachine.setNewPrimaryReversePath(f.getReversePathId());
        stateMachine.setNewProtectedForwardPath(f.getProtectedForwardPathId());
        stateMachine.setNewProtectedReversePath(f.getProtectedReversePathId());

        stateMachine.saveActionToHistory("The flow paths were updated");
        CommandContext commandContext = stateMachine.getCommandContext();
        InfoData flowData = new FlowResponse(FlowMapper.INSTANCE.map(f, getDiverseWithFlowIds(f)));
        Message response = new InfoMessage(flowData, commandContext.getCreateTime(), commandContext.getCorrelationId());
        return Optional.of(response);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap flow paths";
    }
}
