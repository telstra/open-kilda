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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.action;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class UpdateFlowPathsAction extends NbTrackableAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    public UpdateFlowPathsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowPathSwapContext context,
                                                    FlowPathSwapFsm stateMachine) {

        Flow f = transactionManager.doInTransaction(() -> {
            String flowId = stateMachine.getFlowId();
            Flow flow = getFlow(flowId);

            log.debug("Swapping primary and protected paths for flow {}", flowId);

            FlowPath oldPrimaryForward = flow.getForwardPath();
            FlowPath oldPrimaryReverse = flow.getReversePath();
            FlowPath newPrimaryForward = flow.getProtectedForwardPath();
            FlowPath newPrimaryReverse = flow.getProtectedReversePath();

            setMirrorPointsToNewPath(oldPrimaryForward.getPathId(), newPrimaryForward.getPathId());
            setMirrorPointsToNewPath(oldPrimaryReverse.getPathId(), newPrimaryReverse.getPathId());

            flow.setForwardPath(newPrimaryForward);
            flow.setReversePath(newPrimaryReverse);
            flow.setProtectedForwardPath(oldPrimaryForward);
            flow.setProtectedReversePath(oldPrimaryReverse);

            return flow;
        });

        stateMachine.setNewPrimaryForwardPath(f.getForwardPathId());
        stateMachine.setNewPrimaryReversePath(f.getReversePathId());
        stateMachine.setNewProtectedForwardPath(f.getProtectedForwardPathId());
        stateMachine.setNewProtectedReversePath(f.getProtectedReversePathId());

        stateMachine.saveActionToHistory("The flow paths were updated");
        CommandContext commandContext = stateMachine.getCommandContext();
        InfoData flowData =
                new FlowResponse(FlowMapper.INSTANCE.map(f, getDiverseWithFlowIds(f), getFlowMirrorPaths(f)));
        Message response = new InfoMessage(flowData, commandContext.getCreateTime(), commandContext.getCorrelationId());
        return Optional.of(response);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap flow paths";
    }
}
