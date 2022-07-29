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
import static java.util.Collections.emptyList;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.command.yflow.YFlowRerouteResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class OnSubFlowAllocatedAction extends
        NbTrackableWithHistorySupportAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final YFlowRepository yFlowRepository;

    public OnSubFlowAllocatedAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
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

        if (stateMachine.getAllocatedSubFlows().size() == stateMachine.getSubFlows().size()) {
            return Optional.of(buildRerouteResponseMessage(stateMachine));
        }

        return Optional.empty();

    }

    private Message buildRerouteResponseMessage(YFlowRerouteFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        List<Long> oldFlowPathCookies = stateMachine.getOldYFlowPathCookies();
        List<FlowPath> flowPaths = transactionManager.doInTransaction(() -> {
            YFlow yflow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            SwitchId sharedSwitchId = yflow.getSharedEndpoint().getSwitchId();
            List<FlowPath> paths = new ArrayList<>();
            for (YSubFlow subFlow : yflow.getSubFlows()) {
                Flow flow = subFlow.getFlow();
                FlowPath flowPath = flow.getPaths().stream()
                        .filter(path -> sharedSwitchId.equals(path.getSrcSwitchId())
                                && !path.isProtected()
                                && !oldFlowPathCookies.contains(path.getCookie().getValue()))
                        .findFirst()
                        .orElse(sharedSwitchId.equals(flow.getForwardPath().getSrcSwitchId()) ? flow.getForwardPath()
                                : flow.getReversePath());
                paths.add(flowPath);
            }
            return paths;
        });

        List<FlowPath> nonEmptyPaths = flowPaths.stream()
                .filter(fp -> !fp.getSegments().isEmpty()).collect(Collectors.toList());
        PathInfoData sharedPath = FlowPathMapper.INSTANCE.map(nonEmptyPaths.size() >= 2
                ? IntersectionComputer.calculatePathIntersectionFromSource(nonEmptyPaths) : emptyList());

        List<SubFlowPathDto> subFlowPathDtos = flowPaths.stream()
                .map(flowPath -> new SubFlowPathDto(flowPath.getFlowId(), FlowPathMapper.INSTANCE.map(flowPath)))
                .sorted(Comparator.comparing(SubFlowPathDto::getFlowId))
                .collect(Collectors.toList());

        PathInfoData oldSharedPath = stateMachine.getOldSharedPath();
        List<SubFlowPathDto> oldSubFlowPathDtos = stateMachine.getOldSubFlowPathDtos();

        YFlowRerouteResponse response = new YFlowRerouteResponse(sharedPath, subFlowPathDtos,
                !(sharedPath.equals(oldSharedPath) && subFlowPathDtos.equals(oldSubFlowPathDtos)));
        CommandContext commandContext = stateMachine.getCommandContext();
        return new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}
