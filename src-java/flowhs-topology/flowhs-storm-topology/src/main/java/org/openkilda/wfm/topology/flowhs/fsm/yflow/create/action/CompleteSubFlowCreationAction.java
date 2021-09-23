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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

import static java.lang.String.format;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class CompleteSubFlowCreationAction extends
        YFlowProcessingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final FlowRepository flowRepository;

    public CompleteSubFlowCreationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        List<SubFlowDto> targetSubFlows = stateMachine.getTargetFlow().getSubFlows();

        log.debug("Start creating {} sub-flow references to y-flow {}", targetSubFlows.size(),
                stateMachine.getYFlowId());

        transactionManager.doInTransaction(() -> {
            YFlow yFlow = getYFlow(yFlowId);
            Set<YSubFlow> subFlows = stateMachine.getSubFlows().stream()
                    .map(flowId -> {
                        Flow flow = flowRepository.findById(flowId)
                                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                                        format("Flow %s not found", flowId)));
                        SubFlowDto subFlowDto = targetSubFlows.stream()
                                .filter(f -> f.getFlowId().equals(flowId))
                                .findAny()
                                .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                                        format("Can't find definition of reated sub-flow %s", flowId)));
                        SubFlowSharedEndpointEncapsulation sharedEndpoint = subFlowDto.getSharedEndpoint();
                        FlowEndpoint endpoint = subFlowDto.getEndpoint();

                        return YSubFlow.builder()
                                .yFlow(yFlow)
                                .subFlow(flow)
                                .sharedEndpointVlan(sharedEndpoint.getVlanId())
                                .sharedEndpointInnerVlan(sharedEndpoint.getInnerVlanId())
                                .endpointSwitchId(endpoint.getSwitchId())
                                .endpointPort(endpoint.getPortNumber())
                                .endpointVlan(endpoint.getOuterVlanId())
                                .endpointInnerVlan(endpoint.getInnerVlanId())
                                .build();
                    })
                    .collect(Collectors.toSet());
            yFlow.setSubFlows(subFlows);
        });
        stateMachine.saveActionToHistory("Completed sub-flow creation for the y-flow");
    }
}
