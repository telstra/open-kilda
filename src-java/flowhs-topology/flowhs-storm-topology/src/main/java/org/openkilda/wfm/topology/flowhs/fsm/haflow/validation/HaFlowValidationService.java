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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.validation;

import static org.openkilda.messaging.model.FlowDirectionType.FORWARD;
import static org.openkilda.messaging.model.FlowDirectionType.PROTECTED_FORWARD;
import static org.openkilda.messaging.model.FlowDirectionType.PROTECTED_REVERSE;
import static org.openkilda.messaging.model.FlowDirectionType.REVERSE;

import org.openkilda.messaging.command.haflow.HaFlowValidationResponse;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.model.FlowDirectionType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRuleComparator;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRuleConverter;

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
public class HaFlowValidationService {

    private final HaFlowRepository haFlowRepository;
    private final SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();
    private final SimpleSwitchRuleComparator simpleSwitchRuleComparator;
    private final RuleManager ruleManager;
    private final PersistenceManager persistenceManager;

    public HaFlowValidationService(@NonNull PersistenceManager persistenceManager, RuleManager ruleManager) {
        this.haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        this.ruleManager = ruleManager;
        this.persistenceManager = persistenceManager;
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.simpleSwitchRuleComparator = new SimpleSwitchRuleComparator(switchRepository);
    }

    /**
     * Finds a valid {@link HaFlow} by ID.
     *
     * @param haFlowId the ID of the {@link HaFlow} to find
     * @return the {@link HaFlow} with the specified ID if it is in a valid state
     * @throws FlowNotFoundException     if the {@link HaFlow} with the specified ID is not found
     * @throws IllegalFlowStateException if the {@link HaFlow} with the specified ID is found
     *                                   but is not in a valid state
     */
    public HaFlow findValidHaFlowById(String haFlowId) throws FlowNotFoundException, IllegalFlowStateException {
        HaFlow haFlow = haFlowRepository.findById(haFlowId).orElseThrow(() -> new FlowNotFoundException(haFlowId));
        if (FlowStatus.DOWN.equals(haFlow.getStatus())) {
            throw new IllegalFlowStateException(haFlowId);
        }
        return haFlow;
    }

    /**
     * Validates the specified ha-flow by comparing its expected speaker data with the actual speaker data received
     * from the switches. The method builds the expected speaker data by retrieving the required ha-flow data from the
     * persistence layer and converts it into a simple switch rule format. It then compares the expected speaker
     * data with the actual speaker data and creates a validation response object containing discrepancies if any.
     *
     * @param haFlowId            the ID of the ha-flow to validate
     * @param flowDumpResponses   the list of ha-flow dump responses received from switches
     * @param metersDumpResponses the list of meter dump responses received from switches
     * @param groupDumpResponses  the list of group dump responses received from switches
     * @param switchIdSet         the set of switches for which speaker data is required
     * @return a HaFlowValidationResponse object containing validation results for the specified ha-flow
     * @throws FlowNotFoundException   if the specified ha-flow is not found in the persistence layer
     * @throws SwitchNotFoundException if speaker data is not found for one or more switches
     *                                 specified in the switchIdSet
     */
    public HaFlowValidationResponse validateFlow(String haFlowId, List<FlowDumpResponse> flowDumpResponses,
                                                 List<MeterDumpResponse> metersDumpResponses,
                                                 List<GroupDumpResponse> groupDumpResponses,
                                                 Set<SwitchId> switchIdSet)
            throws FlowNotFoundException, SwitchNotFoundException {

        Optional<HaFlow> foundHaFlow = haFlowRepository.findById(haFlowId);
        if (!foundHaFlow.isPresent()) {
            throw new FlowNotFoundException(haFlowId);
        }
        HaFlow haFlow = foundHaFlow.get();

        if (haFlow.getForwardPath() == null) {
            throw new InvalidPathException(haFlowId, "The forward path has not been returned.");
        }
        if (haFlow.getReversePath() == null) {
            throw new InvalidPathException(haFlowId, "The reverse path has not been returned.");
        }

        List<SpeakerData> actualSpeakerData = flowDumpResponses.stream()
                .flatMap(e -> e.getFlowSpeakerData().stream()).collect(Collectors.toList());
        int rulesCount = actualSpeakerData.size();
        actualSpeakerData.addAll(metersDumpResponses.stream()
                .flatMap(e -> e.getMeterSpeakerData().stream()).collect(Collectors.toList()));
        int metersCount = new Long(metersDumpResponses.stream()
                .mapToLong(e -> e.getMeterSpeakerData().size()).sum()).intValue();
        actualSpeakerData.addAll(groupDumpResponses.stream()
                .flatMap(e -> e.getGroupSpeakerData().stream()).collect(Collectors.toList()));

        Map<SwitchId, List<SimpleSwitchRule>> actualSimpleSwitchRulesBySwitchId
                = simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(actualSpeakerData);
        PersistenceDataAdapter dataAdapter = getDataAdapter(switchIdSet, haFlow.getPaths()
                .stream().flatMap(haFlowPath -> haFlowPath.getSubPaths().stream())
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet()));

        HaFlowValidationResponse flowValidationResponse = new HaFlowValidationResponse();
        flowValidationResponse.setSubFlowValidationResults(new ArrayList<>());
        boolean filterOutUsedSharedRules = false;

        for (HaFlowPath haFlowPath : haFlow.getPaths()) {
            FlowDirectionType directionType;
            if (haFlowPath.isForward()) {
                directionType = haFlowPath.isProtected() ? PROTECTED_FORWARD : FORWARD;
            } else {
                directionType = haFlowPath.isProtected() ? PROTECTED_REVERSE : REVERSE;
            }

            List<SpeakerData> expectedForwardRules =
                    buildExpectedSpeakerData(haFlowPath, filterOutUsedSharedRules, dataAdapter);
            FlowValidationResponse validationResponse = compareAndGetDiscrepancies(
                    actualSimpleSwitchRulesBySwitchId,
                    simpleSwitchRuleConverter
                            .convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(expectedForwardRules)
                            .values().stream().flatMap(Collection::stream).collect(Collectors.toList()),
                    haFlowId,
                    rulesCount,
                    metersCount,
                    directionType);
            flowValidationResponse.getSubFlowValidationResults().add(validationResponse);
        }
        if (flowValidationResponse.getSubFlowValidationResults().stream()
                .allMatch(FlowValidationResponse::getAsExpected)) {
            flowValidationResponse.setAsExpected(true);
        }
        return flowValidationResponse;
    }

    private PersistenceDataAdapter getDataAdapter(Set<SwitchId> switchIds, Set<PathId> pathIds) {
        return PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(switchIds)
                .pathIds(pathIds)
                .build();
    }

    private List<SpeakerData> buildExpectedSpeakerData(HaFlowPath flowPath, boolean filterOutUsedSharedRules,
                                                       PersistenceDataAdapter dataAdapter) {
        return ruleManager.buildRulesHaFlowPath(
                flowPath,
                filterOutUsedSharedRules,
                dataAdapter);
    }

    private FlowValidationResponse compareAndGetDiscrepancies(Map<SwitchId, List<SimpleSwitchRule>> actualRules,
                                                              List<SimpleSwitchRule> expectedRulesFromDb,
                                                              String haFlowId,
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
                .flowId(haFlowId)
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
