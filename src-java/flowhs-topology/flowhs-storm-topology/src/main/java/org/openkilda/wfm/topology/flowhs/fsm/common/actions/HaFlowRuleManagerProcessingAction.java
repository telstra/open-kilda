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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.model.HaFlow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.FlowRulesConverter;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class HaFlowRuleManagerProcessingAction<T extends FlowProcessingWithHistorySupportFsm<
        T, S, E, C, ?, ?>, S, E, C> extends HaFlowProcessingWithHistorySupportAction<T, S, E, C> {
    protected final RuleManager ruleManager;

    protected HaFlowRuleManagerProcessingAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.ruleManager = ruleManager;
    }

    protected Collection<InstallSpeakerCommandsRequest> buildHaFlowInstallRequests(
            Collection<SpeakerData> speakerData, CommandContext context, boolean failIfExist) {
        return FlowRulesConverter.INSTANCE.buildFlowInstallCommands(speakerData, context, failIfExist);
    }

    protected Collection<DeleteSpeakerCommandsRequest> buildHaFlowDeleteRequests(
            Collection<SpeakerData> speakerData, CommandContext context) {
        return FlowRulesConverter.INSTANCE.buildFlowDeleteCommands(speakerData, context);
    }

    protected Map<PathId, HaFlow> buildHaFlowMap(
            HaFlow originalHaFlow, HaPathIdsPair primaryPathIds, HaPathIdsPair protectedPathIds) {
        Map<PathId, HaFlow> result = buildHaFlowMap(originalHaFlow, primaryPathIds);
        result.putAll(buildHaFlowMap(originalHaFlow, protectedPathIds));
        return result;
    }

    protected Map<PathId, HaFlow> buildHaFlowMap(HaFlow originalHaFlow, HaPathIdsPair haPathIdsPair) {
        Map<PathId, HaFlow> result = new HashMap<>();
        if (haPathIdsPair != null) {
            for (PathId subPathId : haPathIdsPair.getAllSubPathIds()) {
                result.put(subPathId, originalHaFlow);
            }
            for (PathId haFlowPathId : haPathIdsPair.getAllHaFlowPathIds()) {
                result.put(haFlowPathId, originalHaFlow);
            }
        }
        return result;
    }

    protected HaFlow copyHaFlowWithPathIds(
            HaFlow sourceHaFlow, HaPathIdsPair primaryPathIds, HaPathIdsPair protectedPathIds) {
        HaFlow updatedHaFlow = new HaFlow(sourceHaFlow);
        if (primaryPathIds != null) {
            updatedHaFlow.setForwardPathId(primaryPathIds.getForward().getHaPathId());
            updatedHaFlow.setReversePathId(primaryPathIds.getReverse().getHaPathId());
        }
        if (protectedPathIds != null) {
            updatedHaFlow.setProtectedForwardPathId(protectedPathIds.getForward().getHaPathId());
            updatedHaFlow.setProtectedReversePathId(protectedPathIds.getReverse().getHaPathId());
        }
        return updatedHaFlow;
    }
}
