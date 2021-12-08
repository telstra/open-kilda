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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnSubFlowAllocatedAction extends NbTrackableAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final FlowRerouteService flowRerouteService;
    private final YFlowRepository yFlowRepository;

    public OnSubFlowAllocatedAction(FlowRerouteService flowRerouteService, PersistenceManager persistenceManager) {
        super(persistenceManager);
        this.flowRerouteService = flowRerouteService;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
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

        if (subFlowId.equals(stateMachine.getMainAffinityFlowId())) {
            stateMachine.getRerouteRequests().forEach(rerouteRequest -> {
                String requestedFlowId = rerouteRequest.getFlowId();
                if (!requestedFlowId.equals(subFlowId)) {
                    // clear to avoid the 'path has no affected ISLs' exception
                    rerouteRequest.getAffectedIsl().clear();
                    stateMachine.addSubFlow(requestedFlowId);
                    stateMachine.addReroutingSubFlow(requestedFlowId);
                    stateMachine.notifyEventListeners(listener ->
                            listener.onSubFlowProcessingStart(yFlowId, requestedFlowId));
                    CommandContext flowContext = stateMachine.getCommandContext().fork(requestedFlowId);
                    flowRerouteService.startFlowRerouting(rerouteRequest, flowContext, yFlowId);
                }
            });
        }

        if (stateMachine.getAllocatedSubFlows().size() == stateMachine.getSubFlows().size()) {
            YFlow yflow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            return Optional.of(buildResponseMessage(yflow, stateMachine.getCommandContext()));
        }

        return Optional.empty();

    }

    private Message buildResponseMessage(YFlow yFlow, CommandContext commandContext) {
        YFlowResponse response = YFlowResponse.builder().yFlow(YFlowMapper.INSTANCE.toYFlowDto(yFlow)).build();
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}
