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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions;

import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class StartReroutingYFlowAction
        extends YFlowRuleManagerProcessingAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    public StartReroutingYFlowAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowRerouteContext context,
                           YFlowRerouteFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        List<FlowPath> flowPaths = transactionManager.doInTransaction(() -> {
            YFlow yFlow = getYFlow(yFlowId);
            saveOldResources(stateMachine, yFlow);
            stateMachine.setDeleteOldYFlowCommands(buildYFlowDeleteRequests(yFlow, stateMachine.getCommandContext()));

            SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();
            return yFlow.getSubFlows().stream()
                    .map(YSubFlow::getFlow)
                    .flatMap(flow -> Stream.of(flow.getForwardPath(), flow.getReversePath()))
                    .filter(path -> sharedSwitchId.equals(path.getSrcSwitchId()))
                    .collect(Collectors.toList());
        });
        stateMachine.setOldYFlowPathCookies(flowPaths.stream()
                .map(FlowPath::getCookie).map(FlowSegmentCookie::getValue).collect(Collectors.toList()));

        List<PathSegment> sharedPathSegments = IntersectionComputer.calculatePathIntersectionFromSource(flowPaths);
        PathInfoData sharedPath = FlowPathMapper.INSTANCE.map(sharedPathSegments);
        stateMachine.setOldSharedPath(sharedPath);

        List<SubFlowPathDto> subFlowPathDtos = flowPaths.stream()
                .map(flowPath -> new SubFlowPathDto(flowPath.getFlowId(), FlowPathMapper.INSTANCE.map(flowPath)))
                .sorted(Comparator.comparing(SubFlowPathDto::getFlowId))
                .collect(Collectors.toList());
        stateMachine.setOldSubFlowPathDtos(subFlowPathDtos);
    }

    private void saveOldResources(YFlowRerouteFsm stateMachine, YFlow yFlow) {
        YFlowResources oldYFlowResources = new YFlowResources();
        oldYFlowResources.setMainPathYPointResources(EndpointResources.builder()
                .endpoint(yFlow.getYPoint())
                .meterId(yFlow.getMeterId())
                .build());
        oldYFlowResources.setProtectedPathYPointResources(EndpointResources.builder()
                .endpoint(yFlow.getProtectedPathYPoint())
                .meterId(yFlow.getProtectedPathMeterId())
                .build());
        oldYFlowResources.setSharedEndpointResources(EndpointResources.builder()
                .endpoint(yFlow.getSharedEndpoint().getSwitchId())
                .meterId(yFlow.getSharedEndpointMeterId())
                .build());
        stateMachine.setOldResources(oldYFlowResources);
    }
}
