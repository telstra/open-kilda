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
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.fsm.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collection;

@Slf4j
public class RevertResourceAllocationAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final TransactionManager transactionManager;
    private final FlowResourcesManager resourcesManager;

    public RevertResourceAllocationAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.transactionManager = persistenceManager.getTransactionManager();
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                           FlowRerouteFsm.Event event, FlowRerouteContext context,
                           FlowRerouteFsm stateMachine) {
        Collection<FlowResources> newResources = stateMachine.getNewResources();
        log.debug("Reverting resource allocation {}.", newResources);

        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId());

            flow.setStatus(stateMachine.getOriginalFlowStatus());
            flowRepository.createOrUpdate(flow);

            if (stateMachine.getNewPrimaryForwardPath() != null
                    && stateMachine.getNewPrimaryReversePath() != null) {
                FlowPath newForward = getFlowPath(flow, stateMachine.getNewPrimaryForwardPath());
                FlowPath newReverse = getFlowPath(flow, stateMachine.getNewPrimaryReversePath());

                log.debug("Removing the new primary paths {} / {}", newForward, newReverse);

                flowPathRepository.delete(newForward);
                flowPathRepository.delete(newReverse);
            }

            if (stateMachine.getNewProtectedForwardPath() != null
                    && stateMachine.getNewProtectedReversePath() != null) {
                FlowPath newForward = getFlowPath(flow, stateMachine.getNewProtectedForwardPath());
                FlowPath newReverse = getFlowPath(flow, stateMachine.getNewProtectedReversePath());

                log.debug("Removing the new protected paths {} / {}", newForward, newReverse);

                flowPathRepository.delete(newForward);
                flowPathRepository.delete(newReverse);
            }

            if (newResources != null) {
                newResources.forEach(flowResources -> {
                    resourcesManager.deallocatePathResources(flowResources);

                    log.debug("Resources has been de-allocated: {}", flowResources);

                    saveHistory(flow, flowResources, stateMachine);
                });
            }
        });
    }

    private void saveHistory(Flow flow, FlowResources resources, FlowRerouteFsm stateMachine) {
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
