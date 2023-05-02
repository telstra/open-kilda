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

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RemoveOldRulesAction extends
        HaFlowRuleManagerProcessingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public RemoveOldRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        HaFlow originalHaFlow = stateMachine.getOriginalHaFlow();
        DataAdapter dataAdapter = buildDataAdapter(originalHaFlow, stateMachine);

        boolean isPartialUpdate = !stateMachine.getPartialUpdateEndpoints().isEmpty();

        HaFlowPath forwardPath = getHaFlowPath(originalHaFlow, originalHaFlow.getForwardPathId());
        List<SpeakerData> commands = new ArrayList<>(ruleManager.buildRulesHaFlowPath(
                forwardPath, true, isPartialUpdate, true, true, dataAdapter));

        HaFlowPath reversePath = getHaFlowPath(originalHaFlow, originalHaFlow.getReversePathId());
        commands.addAll(ruleManager.buildRulesHaFlowPath(
                reversePath, true, isPartialUpdate, true, true, dataAdapter));

        if (originalHaFlow.isAllocateProtectedPath()) {
            HaFlowPath protectedForwardPath = getHaFlowPath(originalHaFlow, originalHaFlow.getProtectedForwardPathId());
            commands.addAll(ruleManager.buildRulesHaFlowPath(
                    protectedForwardPath, true, isPartialUpdate, false, true, dataAdapter));

            HaFlowPath protectedReversePath = getHaFlowPath(originalHaFlow, originalHaFlow.getProtectedReversePathId());
            commands.addAll(ruleManager.buildRulesHaFlowPath(
                    protectedReversePath, true, isPartialUpdate, false, true, dataAdapter));
        }

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove old rules");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            Collection<DeleteSpeakerCommandsRequest> deleteRequests = buildHaFlowDeleteRequests(
                    commands, stateMachine.getCommandContext());

            for (DeleteSpeakerCommandsRequest request : deleteRequests) {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            }
            stateMachine.saveActionToHistory("Commands for removing old rules have been sent");
        }
    }

    private DataAdapter buildDataAdapter(HaFlow originalHaFlow, HaFlowUpdateFsm stateMachine) {
        Set<PathId> pathIds = newHashSet(stateMachine.getOldPrimaryPathIds().getAllSubPathIds());
        if (stateMachine.getOldProtectedPathIds() != null) {
            pathIds.addAll(stateMachine.getOldProtectedPathIds().getAllSubPathIds());
        }
        for (SwitchId switchId : originalHaFlow.getEndpointSwitchIds()) {
            pathIds.addAll(flowPathRepository.findBySrcSwitch(switchId, false).stream()
                    .map(FlowPath::getPathId)
                    .collect(Collectors.toSet()));
        }

        Set<SwitchId> switchIds = new HashSet<>();
        if (stateMachine.getPartialUpdateEndpoints().isEmpty()) {
            switchIds.addAll(originalHaFlow.getEndpointSwitchIds());
            switchIds.addAll(getSubPathSwitchIds(stateMachine.getOldPrimaryPathIds().getAllSubPathIds()));
            if (stateMachine.getOldProtectedPathIds() != null) {
                switchIds.addAll(getSubPathSwitchIds(stateMachine.getOldProtectedPathIds().getAllSubPathIds()));
            }
        } else {
            switchIds.addAll(stateMachine.getPartialUpdateEndpoints());
        }

        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(
                originalHaFlow, stateMachine.getOldPrimaryPathIds(), stateMachine.getOldProtectedPathIds());

        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false, additionalHaFlowMap);
    }

    private Set<SwitchId> getSubPathSwitchIds(Collection<PathId> subPathIds) {
        Set<SwitchId> switchIds = new HashSet<>();
        for (PathId subPathId : subPathIds) {
            FlowPath subPath = flowPathRepository.findById(subPathId)
                    .orElseThrow(() -> new IllegalStateException(format("HA-sub path %s not found", subPathId)));
            for (PathSegment segment : subPath.getSegments()) {
                switchIds.add(segment.getSrcSwitchId());
                switchIds.add(segment.getDestSwitchId());
            }
        }
        return switchIds;
    }
}
