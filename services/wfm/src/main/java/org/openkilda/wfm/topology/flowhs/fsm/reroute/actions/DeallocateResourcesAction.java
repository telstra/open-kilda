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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;
import java.util.Collection;

@Slf4j
public class DeallocateResourcesAction extends
        AnonymousAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final TransactionManager transactionManager;
    private final FlowResourcesManager resourcesManager;

    public DeallocateResourcesAction(PersistenceManager persistenceManager,
                                     FlowResourcesManager resourcesManager) {
        transactionManager = persistenceManager.getTransactionManager();
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void execute(State from, State to,
                        Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        try {
            Collection<FlowResources> oldResources = stateMachine.getOldResources();
            transactionManager.doInTransaction(() ->
                    oldResources.forEach(flowResources -> {
                        resourcesManager.deallocatePathResources(flowResources);

                        log.debug("Resources has been de-allocated: {}", flowResources);

                        saveHistory(flowResources, stateMachine);
                    }));
        } catch (Exception e) {
            log.error("Failed to deallocate resources", e);

            stateMachine.fireError();
        }
    }

    private void saveHistory(FlowResources resources, FlowRerouteFsm stateMachine) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Flow resources were deallocated")
                        .time(Instant.now())
                        .description(format("Flow resources for %s/%s were deallocated",
                                resources.getForward().getPathId(), resources.getReverse().getPathId()))
                        .flowId(stateMachine.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
