/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowRerouteResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowPaths;
import org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, HaFlowRerouteContext context,
                                                    HaFlowRerouteFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();

        PathId newForwardPathId = Optional.ofNullable(stateMachine.getNewPrimaryPathIds())
                .map(HaPathIdsPair::getForward)
                .map(HaFlowPathIds::getHaPathId)
                .orElse(null);

        HaFlowPath newForwardPath = newForwardPathId == null ? null : getHaFlowPath(newForwardPathId);
        HaFlowPath currentForwardPath = stateMachine.getOriginalHaFlow().getForwardPath();

        if (stateMachine.getNewPrimaryPathIds() == null && stateMachine.getNewProtectedPathIds() == null) {
            stateMachine.fireError(Event.REROUTE_IS_SKIPPED, "Reroute is unsuccessful. Couldn't find new path(s)");
        } else {
            if (stateMachine.isEffectivelyDown()) {
                log.warn("HA-flow {} is mentioned as effectively DOWN, so it will be forced to DOWN state if reroute "
                                + "fail", haFlowId);
                stateMachine.setOriginalFlowStatus(FlowStatus.DOWN);
            }

            // Notify about successful allocation.
            stateMachine.notifyEventListeners(listener -> listener.onResourcesAllocated(haFlowId));
        }

        return Optional.of(buildRerouteResponseMessage(currentForwardPath, newForwardPath,
                stateMachine.getCommandContext()));
    }

    private Message buildRerouteResponseMessage(
            HaFlowPath currentForward, HaFlowPath newForward, CommandContext commandContext) {
        YFlowPaths currentPaths = Optional.ofNullable(currentForward)
                .map(HaFlowPath::getSubPaths)
                .map(HaFlowUtils::definePaths)
                .orElse(YFlowPaths.buildEmpty());
        YFlowPaths newPaths = Optional.ofNullable(newForward)
                .map(HaFlowPath::getSubPaths)
                .map(HaFlowUtils::definePaths)
                .orElse(currentPaths);

        HaFlowRerouteResponse response = new HaFlowRerouteResponse(
                newPaths.getSharedPath(), newPaths.getSubFlowPaths(), !currentPaths.isSamePath(newPaths));
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }

    @Override
    protected void handleError(HaFlowRerouteFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
