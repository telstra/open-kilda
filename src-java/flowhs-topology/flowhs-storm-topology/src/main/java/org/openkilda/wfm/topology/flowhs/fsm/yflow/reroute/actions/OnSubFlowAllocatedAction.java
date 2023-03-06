/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowRerouteResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowPaths;
import org.openkilda.wfm.topology.flowhs.utils.YFlowUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnSubFlowAllocatedAction extends
        NbTrackableWithHistorySupportAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final YFlowRepository yFlowRepository;
    private final YFlowUtils utils;

    public OnSubFlowAllocatedAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        utils = new YFlowUtils(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowRerouteContext context,
                                                    YFlowRerouteFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isReroutingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        String yFlowId = stateMachine.getYFlowId();
        stateMachine.saveActionToHistory("Rerouting a sub-flow",
                format("Allocated resources for sub-flow %s of y-flow %s", subFlowId, yFlowId));

        stateMachine.addAllocatedSubFlow(subFlowId);

        if (stateMachine.getAllocatedSubFlows().size() == stateMachine.getSubFlows().size()) {
            return Optional.of(buildRerouteResponseMessage(stateMachine));
        }

        return Optional.empty();

    }

    private Message buildRerouteResponseMessage(YFlowRerouteFsm stateMachine) {
        YFlowPaths paths = utils.definePaths(stateMachine.getYFlowId(), stateMachine.getOldYFlowPathCookies());
        boolean rerouted = !(
                paths.getSharedPath().equals(stateMachine.getOldSharedPath())
                        && paths.getSubFlowPaths().equals(stateMachine.getOldSubFlowPathDtos()));
        YFlowRerouteResponse response = new YFlowRerouteResponse(
                paths.getSharedPath(), paths.getSubFlowPaths(), rerouted);
        CommandContext commandContext = stateMachine.getCommandContext();
        return new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}
