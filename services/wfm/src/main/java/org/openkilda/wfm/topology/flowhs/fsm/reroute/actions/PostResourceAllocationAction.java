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
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowRerouteContext context,
                                                    FlowRerouteFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.NO_RELATIONS);
        FlowPath currentForwardPath = getFlowPath(flow.getForwardPathId());

        if (stateMachine.getNewPrimaryForwardPath() == null && stateMachine.getNewPrimaryReversePath() == null
                && stateMachine.getNewProtectedForwardPath() == null
                && stateMachine.getNewProtectedReversePath() == null) {
            stateMachine.fireRerouteIsSkipped("Reroute is unsuccessful. Couldn't find new path(s)");
        }

        FlowPath newForwardPath = null;
        if (stateMachine.getNewPrimaryForwardPath() != null) {
            newForwardPath = getFlowPath(stateMachine.getNewPrimaryForwardPath()).getPath();
        }

        return Optional.of(buildRerouteResponseMessage(currentForwardPath, newForwardPath,
                stateMachine.getCommandContext()));
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
}
