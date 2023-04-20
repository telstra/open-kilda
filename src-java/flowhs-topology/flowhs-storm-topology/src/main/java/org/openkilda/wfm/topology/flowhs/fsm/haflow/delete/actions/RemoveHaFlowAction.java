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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class RemoveHaFlowAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> {
    FlowRepository flowRepository;

    public RemoveHaFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowDeleteContext context, HaFlowDeleteFsm stateMachine) {
        String haFlowId = stateMachine.getFlowId();

        String affinityGroup = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(haFlowId);
            log.debug("Removing the ha-flow {}", haFlowId);
            haFlowRepository.remove(haFlow);
            return haFlow.getAffinityGroupId();
        });

        stateMachine.saveActionToHistory("HA-flow was removed", String.format("The ha-flow %s was removed", haFlowId));
        updateFlowGroups(haFlowId, affinityGroup);
    }

    private void updateFlowGroups(String haFlowId, String affinityGroupId) {
        // TODO check there is no need to update diversity group
        // TODO check what happen if only one affinity flow left
        if (haFlowId.equals(affinityGroupId)) {
            Collection<String> affinityHaFlowIds = haFlowRepository.findHaFlowIdsByAffinityGroupId(affinityGroupId);
            affinityHaFlowIds
                    .forEach(affinityHaFlowId -> haFlowRepository.updateAffinityFlowGroupId(affinityHaFlowId, null));
            Collection<String> affinityFlowIds = flowRepository.findFlowsIdByAffinityGroupId(affinityGroupId);
            affinityHaFlowIds
                    .forEach(affinityFlowId -> flowRepository.updateAffinityFlowGroupId(affinityFlowId, null));
        }
    }
}
