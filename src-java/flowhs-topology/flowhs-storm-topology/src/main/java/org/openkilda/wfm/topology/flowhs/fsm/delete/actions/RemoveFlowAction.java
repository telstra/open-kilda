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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

@Slf4j
public class RemoveFlowAction extends FlowProcessingAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    public RemoveFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        final String diverseGroupId = flowRepository.getDiverseFlowGroupId(flowId).orElse(null);
        final String affinityGroupId = flowRepository.getAffinityFlowGroupId(flowId).orElse(null);

        log.debug("Removing the flow {}", flowId);
        flowRepository.remove(flowId);

        stateMachine.saveActionToHistory("Flow was removed", String.format("The flow %s was removed", flowId));

        updateFlowGroups(flowId, diverseGroupId, affinityGroupId);
    }

    private void updateFlowGroups(String flowId, String diverseGroupId, String affinityGroupId) {
        if (StringUtils.isNotBlank(diverseGroupId)) {
            Collection<String> diverseFlowIds = flowRepository.findFlowsIdByDiverseGroupId(diverseGroupId);
            if (diverseFlowIds.size() == 1) {
                flowRepository.updateDiverseFlowGroupId(diverseFlowIds.iterator().next(), null);
            }
        }

        if (StringUtils.isNotBlank(affinityGroupId)) {
            Collection<String> affinityFlows = flowRepository.findFlowsIdByAffinityGroupId(affinityGroupId);
            if (affinityFlows.size() == 1) {
                flowRepository.updateAffinityFlowGroupId(affinityFlows.iterator().next(), null);
            } else if (flowId.equals(affinityGroupId)) {
                affinityFlows.forEach(affinityFlowId -> flowRepository.updateAffinityFlowGroupId(affinityFlowId, null));
            }
        }
    }
}
