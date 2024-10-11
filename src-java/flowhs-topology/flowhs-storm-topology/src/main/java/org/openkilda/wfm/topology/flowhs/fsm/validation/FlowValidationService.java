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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

import static org.openkilda.messaging.model.FlowDirectionType.FORWARD;
import static org.openkilda.messaging.model.FlowDirectionType.PROTECTED_FORWARD;
import static org.openkilda.messaging.model.FlowDirectionType.PROTECTED_REVERSE;
import static org.openkilda.messaging.model.FlowDirectionType.REVERSE;

import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.model.FlowDirectionType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;

import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class FlowValidationService {
    private final SwitchRepository switchRepository;
    private final FlowRepository flowRepository;

    private final SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();
    private final SimpleSwitchRuleComparator simpleSwitchRuleComparator;
    private final RuleManager ruleManager;
    private final PersistenceManager persistenceManager;

    public FlowValidationService(@NonNull PersistenceManager persistenceManager,
                                 RuleManager ruleManager) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.ruleManager = ruleManager;
        this.persistenceManager = persistenceManager;

        this.simpleSwitchRuleComparator = new SimpleSwitchRuleComparator(switchRepository);
    }

    /**
     * Check the flow status.
     */
    public Flow checkFlowStatusAndGetFlow(String flowId) throws FlowNotFoundException, IllegalFlowStateException {
        Flow flow = flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));
        if (FlowStatus.DOWN.equals(flow.getStatus())) {
            throw new IllegalFlowStateException(flowId);
        }
        return flow;
    }

    /**
     * Get list of switch ids by flow id.
     */
    public List<SwitchId> getSwitchIdListByFlowId(String flowId) {
        return switchRepository.findSwitchesInFlowPathByFlowId(flowId).stream()
                .map(Switch::getSwitchId)
                .collect(Collectors.toList());
    }

    /**
     * Validate flow.
     */
    public List<FlowValidationResponse> validateFlow(String flowId, List<FlowDumpResponse> flowDumpResponses,
                                                     List<MeterDumpResponse> metersDumpResponses,
                                                     List<GroupDumpResponse> groupDumpResponses,
                                                     Set<SwitchId> switchIdSet)
            throws FlowNotFoundException, SwitchNotFoundException {

        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        if (foundFlow.isEmpty()) {
            throw new FlowNotFoundException(flowId);
        }
        Flow flow = foundFlow.get();

        if (flow.getForwardPath() == null) {
            throw new InvalidPathException(flowId, "Forward path was not returned.");
        }
        if (flow.getReversePath() == null) {
            throw new InvalidPathException(flowId, "Reverse path was not returned.");
        }

        List<SpeakerData> actualSpeakerData = flowDumpResponses.stream()
                .flatMap(e -> e.getFlowSpeakerData().stream()).collect(Collectors.toList());
        List<SpeakerData> speakerDataWithFlowDumpOnly = new ArrayList<>(actualSpeakerData);
        final int rulesCountFromFlowDumpResponsesOnly = speakerDataWithFlowDumpOnly.size();

        actualSpeakerData.addAll(metersDumpResponses.stream()
                .flatMap(e -> e.getMeterSpeakerData().stream()).toList());
        long metersCountLong = metersDumpResponses.stream()
                .mapToLong(e -> e.getMeterSpeakerData().size()).sum();
        if (metersCountLong > (long) Integer.MAX_VALUE) {
            log.warn("metersCount value {} is going to be narrowed", metersCountLong);
        }
        int metersCount = (int) metersCountLong;

        actualSpeakerData.addAll(groupDumpResponses.stream()
                .flatMap(e -> e.getGroupSpeakerData().stream()).toList());

        Map<SwitchId, List<SimpleSwitchRule>> actualSimpleSwitchRulesBySwitchId
                = simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(actualSpeakerData);

        PersistenceDataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(switchIdSet)
                .pathIds(Sets.newHashSet(flow.getForwardPathId(),
                        flow.getReversePathId(),
                        flow.getProtectedForwardPathId(),
                        flow.getProtectedReversePathId()))
                .build();
        List<FlowValidationResponse> flowValidationResponse = new ArrayList<>();

        boolean filterOutUsedSharedRules = false;
        List<SpeakerData> expectedForwardRules = ruleManager.buildRulesForFlowPath(flow.getForwardPath(),
                filterOutUsedSharedRules, dataAdapter);
        flowValidationResponse.add(compareAndGetDiscrepancies(
                actualSimpleSwitchRulesBySwitchId,
                simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(expectedForwardRules)
                        .values().stream().flatMap(Collection::stream).collect(Collectors.toList()),
                flowId,
                rulesCountFromFlowDumpResponsesOnly,
                metersCount,
                FORWARD));

        List<SpeakerData> expectedReversRules = ruleManager.buildRulesForFlowPath(flow.getReversePath(),
                filterOutUsedSharedRules, dataAdapter);
        flowValidationResponse.add(compareAndGetDiscrepancies(
                actualSimpleSwitchRulesBySwitchId,
                simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(expectedReversRules)
                        .values().stream().flatMap(Collection::stream).collect(Collectors.toList()),
                flowId,
                rulesCountFromFlowDumpResponsesOnly,
                metersCount,
                REVERSE));

        if (flow.getProtectedForwardPath() != null) {
            List<SpeakerData> expectProtectedForwardRules = ruleManager.buildRulesForFlowPath(
                    flow.getProtectedForwardPath(),
                    filterOutUsedSharedRules,
                    dataAdapter);

            flowValidationResponse.add(compareAndGetDiscrepancies(
                    actualSimpleSwitchRulesBySwitchId,
                    simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(
                                    expectProtectedForwardRules)
                            .values().stream().flatMap(Collection::stream).collect(Collectors.toList()),
                    flowId,
                    rulesCountFromFlowDumpResponsesOnly,
                    metersCount,
                    PROTECTED_FORWARD));
        }

        if (flow.getProtectedReversePath() != null) {
            List<SpeakerData> expectProtectedReversRules =
                    ruleManager.buildRulesForFlowPath(flow.getProtectedReversePath(),
                            filterOutUsedSharedRules, dataAdapter);
            flowValidationResponse.add(compareAndGetDiscrepancies(
                    actualSimpleSwitchRulesBySwitchId,
                    simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(
                                    expectProtectedReversRules)
                            .values().stream().flatMap(Collection::stream).collect(Collectors.toList()),
                    flowId,
                    rulesCountFromFlowDumpResponsesOnly,
                    metersCount,
                    PROTECTED_REVERSE));
        }

        return flowValidationResponse;
    }

    private FlowValidationResponse compareAndGetDiscrepancies(Map<SwitchId, List<SimpleSwitchRule>> actualRules,
                                                              List<SimpleSwitchRule> expectedRulesFromDb,
                                                              String flowId,
                                                              int totalSwitchRules,
                                                              int metersCount,
                                                              FlowDirectionType flowDirectionType)
            throws SwitchNotFoundException {

        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        List<Long> pktCounts = new ArrayList<>();
        List<Long> byteCounts = new ArrayList<>();
        boolean ingressMirrorFlowIsPresent = false;
        boolean egressMirrorFlowIsPresent = false;
        for (SimpleSwitchRule expectedSimpleRule : expectedRulesFromDb) {
            discrepancies.addAll(simpleSwitchRuleComparator.findDiscrepancy(expectedSimpleRule,
                    actualRules.get(expectedSimpleRule.getSwitchId()), pktCounts, byteCounts));

            if (new FlowSegmentCookie(expectedSimpleRule.getCookie()).isMirror()
                    && expectedSimpleRule.isIngressRule()) {
                ingressMirrorFlowIsPresent = true;
            }

            if (new FlowSegmentCookie(expectedSimpleRule.getCookie()).isMirror() && expectedSimpleRule.isEgressRule()) {
                egressMirrorFlowIsPresent = true;
            }
        }
        int flowMetersCount = (int) expectedRulesFromDb.stream().filter(rule -> rule.getMeterId() != null).count();

        return FlowValidationResponse.builder()
                .flowId(flowId)
                .direction(flowDirectionType)
                .discrepancies(discrepancies)
                .asExpected(discrepancies.isEmpty())
                .pktCounts(pktCounts)
                .byteCounts(byteCounts)
                .flowRulesTotal(expectedRulesFromDb.size())
                .switchRulesTotal(totalSwitchRules)
                .flowMetersTotal(flowMetersCount)
                .switchMetersTotal(metersCount)
                .ingressMirrorFlowIsPresent(ingressMirrorFlowIsPresent)
                .egressMirrorFlowIsPresent(egressMirrorFlowIsPresent)
                .build();
    }
}
