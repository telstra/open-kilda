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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class RevertResourceAllocationAction extends BaseFlowPathRemovalAction {

    private final FlowResourcesManager resourcesManager;

    public RevertResourceAllocationAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);

            FlowResources newPrimaryResources = stateMachine.getNewPrimaryResources();
            if (newPrimaryResources != null) {
                resourcesManager.deallocatePathResources(newPrimaryResources);
                log.debug("Resources has been de-allocated: {}", newPrimaryResources);
                saveHistory(stateMachine, flow, newPrimaryResources);
            }

            FlowResources newProtectedResources = stateMachine.getNewProtectedResources();
            if (newProtectedResources != null) {
                resourcesManager.deallocatePathResources(newProtectedResources);
                log.debug("Resources has been de-allocated: {}", newProtectedResources);
                saveHistory(stateMachine, flow, newProtectedResources);
            }

            FlowPath newPrimaryForward = null;
            FlowPath newPrimaryReverse = null;
            if (stateMachine.getNewPrimaryForwardPath() != null
                    && stateMachine.getNewPrimaryReversePath() != null) {
                newPrimaryForward = getFlowPath(stateMachine.getNewPrimaryForwardPath());
                newPrimaryReverse = getFlowPath(stateMachine.getNewPrimaryReversePath());
            }

            FlowPath newProtectedForward = null;
            FlowPath newProtectedReverse = null;
            if (stateMachine.getNewProtectedForwardPath() != null
                    && stateMachine.getNewProtectedReversePath() != null) {
                newProtectedForward = getFlowPath(stateMachine.getNewProtectedForwardPath());
                newProtectedReverse = getFlowPath(stateMachine.getNewProtectedReversePath());
            }

            flowPathRepository.lockInvolvedSwitches(Stream.of(newPrimaryForward, newPrimaryReverse,
                    newProtectedForward, newProtectedReverse).filter(Objects::nonNull).toArray(FlowPath[]::new));

            if (newPrimaryForward != null && newPrimaryReverse != null) {
                log.debug("Removing the new primary paths {} / {}", newPrimaryForward, newPrimaryReverse);
                deleteFlowPaths(newPrimaryForward, newPrimaryReverse);

                saveHistory(stateMachine, flow.getFlowId(), newPrimaryForward, newPrimaryReverse);
            }

            if (newProtectedForward != null && newProtectedReverse != null) {
                log.debug("Removing the new protected paths {} / {}", newProtectedForward, newProtectedReverse);
                deleteFlowPaths(newProtectedForward, newProtectedReverse);

                saveHistory(stateMachine, flow.getFlowId(), newProtectedForward, newProtectedReverse);
            }
        });

        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewProtectedResources(null);
    }

    private void saveHistory(FlowRerouteFsm stateMachine, Flow flow, FlowResources resources) {
        FlowDumpData flowDumpData = HistoryMapper.INSTANCE.map(flow, resources);
        flowDumpData.setDumpType(DumpType.STATE_BEFORE);
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowDumpData(flowDumpData)
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Flow resources were deallocated")
                        .time(Instant.now())
                        .description(format("Flow resources for %s/%s were deallocated",
                                resources.getForward().getPathId(), resources.getReverse().getPathId()))
                        .flowId(flow.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
