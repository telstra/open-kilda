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

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;

    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    protected Optional<Message> perform(State from, State to,
                                        Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        try {
            FlowPath newForwardPath = null;
            if (stateMachine.getNewPrimaryForwardPath() != null) {
                newForwardPath = flowPathRepository.findById(stateMachine.getNewPrimaryForwardPath())
                        .orElseThrow(() -> new FlowProcessingException(format("Flow path %s not found",
                                stateMachine.getNewPrimaryForwardPath())));
            }

            PathId currentForwardId;
            if (newForwardPath != null) {
                currentForwardId = newForwardPath.getFlow().getForwardPathId();
            } else {
                Flow flow = flowRepository.findById(flowId, FetchStrategy.NO_RELATIONS)
                        .orElseThrow(() -> new FlowProcessingException(format("Flow %s not found", flowId)));
                currentForwardId = flow.getForwardPathId();
            }
            FlowPath currentForwardPath = flowPathRepository.findById(currentForwardId)
                    .orElseThrow(() -> new FlowProcessingException(format("Flow path %s not found",
                            currentForwardId)));

            if (stateMachine.getNewPrimaryForwardPath() == null && stateMachine.getNewPrimaryReversePath() == null
                    && stateMachine.getNewProtectedForwardPath() == null
                    && stateMachine.getNewProtectedReversePath() == null) {
                log.debug("Reroute {} is unsuccessful: can't find new path(s).", flowId);

                stateMachine.fire(Event.REROUTE_IS_SKIPPED);
            }

            return Optional.of(buildRerouteResponseMessage(currentForwardPath, newForwardPath,
                    stateMachine.getCommandContext()));
        } catch (Exception e) {
            String errorDescription = format("Failed to create flow paths for flow %s: %s",
                    flowId, e.getMessage());
            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, errorDescription);

            throw e;
        }
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
