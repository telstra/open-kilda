/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
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
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.FlowRulesConverter;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class YFlowRuleManagerAdapter {
    private final PersistenceManager persistenceManager;
    private final RuleManager ruleManager;

    public YFlowRuleManagerAdapter(PersistenceManager persistenceManager, RuleManager ruleManager) {
        this.persistenceManager = persistenceManager;
        this.ruleManager = ruleManager;
    }

    public Collection<InstallSpeakerCommandsRequest> buildInstallRequests(YFlow yFlow, CommandContext context) {
        List<SpeakerData> speakerData = buildSpeakerData(yFlow);
        return FlowRulesConverter.INSTANCE.buildFlowInstallCommands(speakerData, context);
    }

    public Collection<DeleteSpeakerCommandsRequest> buildDeleteRequests(YFlow yFlow, CommandContext context) {
        List<SpeakerData> speakerData = buildSpeakerData(yFlow);
        return FlowRulesConverter.INSTANCE.buildFlowDeleteCommands(speakerData, context);
    }

    private List<SpeakerData> buildSpeakerData(YFlow yFlow) {
        Set<PathId> pathIds = yFlow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .flatMap(flow -> Stream.of(flow.getForwardPathId(), flow.getReversePathId(),
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<SwitchId> switchIds = Sets.newHashSet(yFlow.getSharedEndpoint().getSwitchId(), yFlow.getYPoint(),
                yFlow.getProtectedPathYPoint());
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(switchIds)
                .pathIds(pathIds)
                .build();
        List<FlowPath> flowPaths = new ArrayList<>(dataAdapter.getCommonFlowPaths().values());

        return ruleManager.buildRulesForYFlow(flowPaths, dataAdapter);
    }
}
