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

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class YFlowRuleManagerProcessingAction<T extends YFlowProcessingFsm<T, S, E, C, ?, ?>, S, E, C>
        extends YFlowProcessingWithHistorySupportAction<T, S, E, C> {
    protected final RuleManager ruleManager;

    public YFlowRuleManagerProcessingAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.ruleManager = ruleManager;
    }

    protected Collection<InstallSpeakerCommandsRequest> buildYFlowInstallCommands(YFlow yFlow, CommandContext context) {
        Map<SwitchId, List<SpeakerData>> speakerData = buildYFlowSpeakerData(yFlow);

        return speakerData.entrySet().stream().map(entry -> {
            SwitchId switchId = entry.getKey();
            List<SpeakerData> dataList = entry.getValue();
            UUID commandId = commandIdGenerator.generate();
            MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
            return new InstallSpeakerCommandsRequest(messageContext, switchId, commandId, mapToOfCommands(dataList));
        }).collect(Collectors.toList());
    }

    protected Collection<DeleteSpeakerCommandsRequest> buildYFlowDeleteCommands(YFlow yFlow, CommandContext context) {
        Map<SwitchId, List<SpeakerData>> speakerData = buildYFlowSpeakerData(yFlow);

        return speakerData.entrySet().stream().map(entry -> {
            SwitchId switchId = entry.getKey();
            List<SpeakerData> dataList = reverseDependenciesForDeletion(entry.getValue());
            UUID commandId = commandIdGenerator.generate();
            MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
            return new DeleteSpeakerCommandsRequest(messageContext, switchId, commandId, mapToOfCommands(dataList));
        }).collect(Collectors.toList());
    }

    private List<SpeakerData> reverseDependenciesForDeletion(List<SpeakerData> source) {
        Map<UUID, Set<UUID>> reversedDependencies = new HashMap<>();
        source.forEach(data ->
                data.getDependsOn().forEach(dependent ->
                        reversedDependencies.computeIfAbsent(dependent, k -> new HashSet<>()).add(data.getUuid())));
        return source.stream()
                .map(data -> {
                    Set<UUID> reversedDependsOn = reversedDependencies.getOrDefault(data.getUuid(), emptySet());
                    if (data instanceof FlowSpeakerData) {
                        return ((FlowSpeakerData) data).toBuilder()
                                .dependsOn(reversedDependsOn)
                                .build();
                    } else if (data instanceof MeterSpeakerData) {
                        return ((MeterSpeakerData) data).toBuilder()
                                .dependsOn(reversedDependsOn)
                                .build();
                    } else if (data instanceof GroupSpeakerData) {
                        return ((GroupSpeakerData) data).toBuilder()
                                .dependsOn(reversedDependsOn)
                                .build();
                    } else {
                        throw new IllegalArgumentException("Unknown speaker data type: " + data);
                    }
                })
                .collect(toList());
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

    private List<OfCommand> mapToOfCommands(List<SpeakerData> speakerData) {
        return speakerData.stream()
                .map(data -> {
                    if (data instanceof GroupSpeakerData) {
                        return new GroupCommand((GroupSpeakerData) data);
                    }
                    if (data instanceof MeterSpeakerData) {
                        return new MeterCommand((MeterSpeakerData) data);
                    }
                    if (data instanceof FlowSpeakerData) {
                        return new FlowCommand((FlowSpeakerData) data);
                    }
                    log.warn("Unsupported speaker data: {}", data);
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(toList());
    }
}
