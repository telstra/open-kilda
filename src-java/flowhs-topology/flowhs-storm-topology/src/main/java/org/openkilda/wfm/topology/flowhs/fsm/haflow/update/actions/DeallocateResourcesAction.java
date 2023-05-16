/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static java.lang.String.format;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeallocateResourcesAction extends
        FlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    private final FlowResourcesManager resourcesManager;

    public DeallocateResourcesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        stateMachine.getOldResources().forEach(haFlowResources -> {
            transactionManager.doInTransaction(() ->
                    resourcesManager.deallocateHaFlowResources(haFlowResources));

            stateMachine.saveActionToHistory("Ha-flow resources were deallocated",
                    format("The ha-flow resources for %s / %s were deallocated",
                            haFlowResources.getForward().getPathId(), haFlowResources.getReverse().getPathId()));
        });

        stateMachine.getRejectedResources().forEach(haFlowResources -> {
            transactionManager.doInTransaction(() ->
                    resourcesManager.deallocateHaFlowResources(haFlowResources));

            stateMachine.saveActionToHistory("Rejected ha-flow resources were deallocated",
                    format("The ha-flow resources for %s / %s were deallocated",
                            haFlowResources.getForward().getPathId(), haFlowResources.getReverse().getPathId()));
        });
    }
}
