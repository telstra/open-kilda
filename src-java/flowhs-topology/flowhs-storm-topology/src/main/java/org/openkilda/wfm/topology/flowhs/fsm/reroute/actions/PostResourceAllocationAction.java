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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableWithHistorySupportAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowRerouteContext context,
                                                    FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        FlowPath newForwardPath = null;
        if (stateMachine.getNewPrimaryForwardPath() != null) {
            newForwardPath = getFlowPath(stateMachine.getNewPrimaryForwardPath());
        }

        PathId currentForwardId;
        if (newForwardPath != null) {
            currentForwardId = newForwardPath.getFlow().getForwardPathId();
        } else {
            Flow flow = getFlow(flowId);
            currentForwardId = flow.getForwardPathId();
        }
        FlowPath currentForwardPath = currentForwardId != null ? getFlowPath(currentForwardId) : null;
        Message rerouteResponse = buildRerouteResponseMessage(currentForwardPath, newForwardPath,
                stateMachine.getCommandContext());
        if (stateMachine.getNewPrimaryForwardPath() == null && stateMachine.getNewPrimaryReversePath() == null
                && stateMachine.getNewProtectedForwardPath() == null
                && stateMachine.getNewProtectedReversePath() == null) {
            stateMachine.setOperationResultMessage(rerouteResponse);
            stateMachine.fireRerouteIsSkipped("Reroute is unsuccessful. Couldn't find new path(s)");
        } else {
            if (stateMachine.isEffectivelyDown()) {
                log.warn("Flow {} is mentioned as effectively DOWN, so it will be forced to DOWN state if reroute fail",
                        flowId);
                stateMachine.setOriginalFlowStatus(FlowStatus.DOWN);
            }

            // Notify about successful allocation.
            stateMachine.notifyEventListeners(listener -> listener.onResourcesAllocated(flowId));
        }

        return Optional.of(rerouteResponse);
    }

    private Message buildRerouteResponseMessage(FlowPath currentForward, FlowPath newForward,
                                                CommandContext commandContext) {
        PathInfoData currentPath = FlowPathMapper.INSTANCE.map(currentForward);
        PathInfoData resultPath = Optional.ofNullable(newForward)
                .map(FlowPathMapper.INSTANCE::map)
                .orElse(currentPath);

        FlowRerouteResponse response = new FlowRerouteResponse(resultPath, !resultPath.equals(currentPath));
        return new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }

    @Override
    protected void handleError(FlowRerouteFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
