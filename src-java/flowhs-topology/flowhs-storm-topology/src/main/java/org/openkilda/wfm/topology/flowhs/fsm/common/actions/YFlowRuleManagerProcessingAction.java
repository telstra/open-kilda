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

import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.FlowRulesConverter;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class YFlowRuleManagerProcessingAction<T extends YFlowProcessingFsm<T, S, E, C, ?, ?>, S, E, C>
        extends YFlowProcessingWithHistorySupportAction<T, S, E, C> {
    protected final RuleManager ruleManager;

    protected YFlowRuleManagerProcessingAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.ruleManager = ruleManager;
    }

    protected Collection<InstallSpeakerCommandsRequest> buildYFlowInstallCommands(YFlow yFlow, CommandContext context) {
        Map<SwitchId, List<SpeakerData>> speakerData = buildYFlowSpeakerData(yFlow);
        return FlowRulesConverter.INSTANCE.buildFlowInstallCommands(speakerData, context);
    }

    protected Collection<DeleteSpeakerCommandsRequest> buildYFlowDeleteCommands(YFlow yFlow, CommandContext context) {
        Map<SwitchId, List<SpeakerData>> speakerData = buildYFlowSpeakerData(yFlow);
        return FlowRulesConverter.INSTANCE.buildFlowDeleteCommands(speakerData, context);
    }

    private Map<SwitchId, List<SpeakerData>> buildYFlowSpeakerData(YFlow yFlow) {
        List<FlowPath> flowPaths = yFlow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .map(Flow::getPaths)
                .flatMap(Collection::stream)
                .collect(toList());

        Set<SwitchId> switchIds = Sets.newHashSet(yFlow.getSharedEndpoint().getSwitchId(), yFlow.getYPoint(),
                yFlow.getProtectedPathYPoint());
        Set<PathId> pathIds = flowPaths.stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(switchIds)
                .pathIds(pathIds)
                .build();

        return ruleManager.buildRulesForYFlow(flowPaths, dataAdapter).stream()
                .collect(Collectors.groupingBy(SpeakerData::getSwitchId,
                        Collectors.mapping(Function.identity(), toList())));
    }
}
