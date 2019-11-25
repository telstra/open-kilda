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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeallocateResourcesAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {
    private final FlowResourcesManager resourcesManager;

    public DeallocateResourcesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() ->
                stateMachine.getOldResources().forEach(flowResources -> {
                    resourcesManager.deallocatePathResources(flowResources);

                    stateMachine.saveActionToHistory("Flow resources were deallocated",
                            format("The flow resources for %s / %s were deallocated",
                                    flowResources.getForward().getPathId(), flowResources.getReverse().getPathId()));
                }));

        stateMachine.resetOldResources();
    }
}
