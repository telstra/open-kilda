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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow;

import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
public class RevertResourceAllocationAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E,
        C extends SpeakerResponseContext> extends BaseHaFlowPathRemovalAction<T, S, E, C> {
    private final FlowResourcesManager resourcesManager;

    public RevertResourceAllocationAction(
            PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());

        List<HaFlowResources> resourcesList = Lists.newArrayList(
                stateMachine.getNewPrimaryResources(), stateMachine.getNewProtectedResources());
        resourcesList.addAll(stateMachine.getRejectedResources());

        resourcesList.stream()
                .filter(Objects::nonNull)
                .forEach(resources -> {
                    transactionManager.doInTransaction(() ->
                            resourcesManager.deallocateHaFlowResources(resources));
                    saveHistory(stateMachine, haFlow, resources);
                });

        removeFlowPaths(stateMachine.getNewPrimaryPathIds());
        removeFlowPaths(stateMachine.getNewProtectedPathIds());
        removeRejectedPaths(stateMachine.getRejectedSubPathsIds(), stateMachine.getRejectedHaPathsIds());

        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewPrimaryPathIds(null);
        stateMachine.setNewProtectedResources(null);
        stateMachine.setNewProtectedPathIds(null);
    }

    private void saveHistory(T stateMachine, HaFlow haFlow, HaFlowResources resources) {
        // TODO save history https://github.com/telstra/open-kilda/issues/5169
        // example org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertResourceAllocationAction
    }
}
