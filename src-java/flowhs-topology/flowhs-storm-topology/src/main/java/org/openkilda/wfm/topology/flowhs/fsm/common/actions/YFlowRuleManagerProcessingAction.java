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

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.OfCommandConverter;
import org.openkilda.wfm.topology.flowhs.utils.YFlowRuleManagerAdapter;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class YFlowRuleManagerProcessingAction<T extends YFlowProcessingFsm<T, S, E, C, ?, ?>, S, E, C>
        extends YFlowProcessingWithHistorySupportAction<T, S, E, C> {
    protected final RuleManager ruleManager;
    private final YFlowRuleManagerAdapter ruleManagerAdapter;

    protected YFlowRuleManagerProcessingAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.ruleManager = ruleManager;

        ruleManagerAdapter = new YFlowRuleManagerAdapter(persistenceManager, ruleManager);
    }

    protected Collection<InstallSpeakerCommandsRequest> buildYFlowInstallRequests(YFlow yFlow, CommandContext context) {
        return ruleManagerAdapter.buildInstallRequests(yFlow, context);
    }

    protected Collection<DeleteSpeakerCommandsRequest> buildYFlowDeleteRequests(YFlow yFlow, CommandContext context) {
        return ruleManagerAdapter.buildDeleteRequests(yFlow, context);
    }

    protected List<SpeakerData> buildYFlowSpeakerData(SwitchId switchId, Set<PathId> pathIds) {
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(singleton(switchId))
                .pathIds(pathIds)
                .build();
        List<FlowPath> flowPaths = new ArrayList<>(dataAdapter.getFlowPaths().values());

        List<SpeakerData> speakerData = ruleManager.buildRulesForYFlow(flowPaths, dataAdapter);
        return speakerData.stream()
                .filter(data -> data.getSwitchId().equals(switchId))
                .collect(toList());
    }

    protected List<OfCommand> buildFlowOnlyOfCommands(SwitchId switchId, Set<PathId> pathIds) {
        List<SpeakerData> speakerData = buildYFlowSpeakerData(switchId, pathIds);
        List<OfCommand> ofCommands = speakerData.stream()
                .filter(data -> data instanceof FlowSpeakerData)
                .map(data -> new FlowCommand((FlowSpeakerData) data))
                .collect(Collectors.toList());
        // We must remove excess deps as take FlowSpeakerData only.
        return OfCommandConverter.INSTANCE.removeExcessDependencies(ofCommands);
    }
}
