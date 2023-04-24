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

import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class FlowValidationService {
    private final SwitchRepository switchRepository;
    private final FlowRepository flowRepository;
    private final FlowResourcesManager flowResourcesManager;

    private final SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();
    private final SimpleSwitchRuleComparator simpleSwitchRuleComparator;

    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    public FlowValidationService(@NonNull PersistenceManager persistenceManager,
                                 @NonNull FlowResourcesManager flowResourcesManager,
                                 long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowResourcesManager = flowResourcesManager;
        this.flowMeterMinBurstSizeInKbits = flowMeterMinBurstSizeInKbits;
        this.flowMeterBurstCoefficient = flowMeterBurstCoefficient;

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
    public List<FlowValidationResponse> validateFlow(String flowId, List<SwitchFlowEntries> switchFlowEntries,
                                                     List<SwitchMeterEntries> switchMeterEntries,
                                                     List<SwitchGroupEntries> switchGroupEntries)
            throws FlowNotFoundException, SwitchNotFoundException {

        Map<SwitchId, List<SimpleSwitchRule>> switchRules = new HashMap<>();
        int rulesCount = 0;
        int metersCount = 0;
        for (SwitchFlowEntries switchRulesEntries : switchFlowEntries) {
            SwitchMeterEntries switchMeters = switchMeterEntries.stream()
                    .filter(meterEntries -> switchRulesEntries.getSwitchId().equals(meterEntries.getSwitchId()))
                    .findFirst()
                    .orElse(null);
            SwitchGroupEntries switchGroup = switchGroupEntries.stream()
                    .filter(groupEntries -> switchRulesEntries.getSwitchId().equals(groupEntries.getSwitchId()))
                    .findFirst()
                    .orElse(null);

            List<SimpleSwitchRule> simpleSwitchRules = simpleSwitchRuleConverter
                    .convertSwitchFlowEntriesToSimpleSwitchRules(switchRulesEntries, switchMeters, switchGroup);
            switchRules.put(switchRulesEntries.getSwitchId(), simpleSwitchRules);

            rulesCount += Optional.ofNullable(switchRulesEntries.getFlowEntries())
                    .map(List::size)
                    .orElse(0);
            metersCount += Optional.ofNullable(switchMeters)
                    .map(SwitchMeterEntries::getMeterEntries)
                    .map(List::size)
                    .orElse(0);
        }

        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        if (!foundFlow.isPresent()) {
            throw new FlowNotFoundException(flowId);
        }
        Flow flow = foundFlow.get();

        if (flow.getForwardPath() == null) {
            throw new InvalidPathException(flowId, "Forward path was not returned.");
        }
        if (flow.getReversePath() == null) {
            throw new InvalidPathException(flowId, "Reverse path was not returned.");
        }

        List<FlowValidationResponse> flowValidationResponse = new ArrayList<>();

        List<SimpleSwitchRule> forwardRules = getSimpleSwitchRules(flow, flow.getForwardPath(), flow.getReversePath());
        flowValidationResponse.add(compare(switchRules, forwardRules, flowId, rulesCount, metersCount));

        List<SimpleSwitchRule> reverseRules = getSimpleSwitchRules(flow, flow.getReversePath(), flow.getForwardPath());
        flowValidationResponse.add(compare(switchRules, reverseRules, flowId, rulesCount, metersCount));

        if (flow.getProtectedForwardPath() != null) {
            List<SimpleSwitchRule> forwardProtectedRules = getSimpleSwitchRules(flow, flow.getProtectedForwardPath(),
                    flow.getProtectedReversePath());
            flowValidationResponse.add(compare(switchRules, forwardProtectedRules, flowId, rulesCount, metersCount));
        }

        if (flow.getProtectedReversePath() != null) {
            List<SimpleSwitchRule> reverseProtectedRules = getSimpleSwitchRules(flow, flow.getProtectedReversePath(),
                    flow.getProtectedForwardPath());
            flowValidationResponse.add(compare(switchRules, reverseProtectedRules, flowId, rulesCount, metersCount));
        }

        return flowValidationResponse;
    }

    private FlowValidationResponse compare(Map<SwitchId, List<SimpleSwitchRule>> rulesPerSwitch,
                                           List<SimpleSwitchRule> rulesFromDb, String flowId,
                                           int totalSwitchRules, int metersCount) throws SwitchNotFoundException {

        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        List<Long> pktCounts = new ArrayList<>();
        List<Long> byteCounts = new ArrayList<>();
        boolean ingressMirrorFlowIsPresent = false;
        boolean egressMirrorFlowIsPresent = false;
        for (SimpleSwitchRule simpleRule : rulesFromDb) {
            discrepancies.addAll(simpleSwitchRuleComparator.findDiscrepancy(simpleRule,
                    rulesPerSwitch.get(simpleRule.getSwitchId()), pktCounts, byteCounts));

            if (new FlowSegmentCookie(simpleRule.getCookie()).isMirror() && simpleRule.isIngressRule()) {
                ingressMirrorFlowIsPresent = true;
            }

            if (new FlowSegmentCookie(simpleRule.getCookie()).isMirror() && simpleRule.isEgressRule()) {
                egressMirrorFlowIsPresent = true;
            }
        }
        int flowMetersCount = (int) rulesFromDb.stream().filter(rule -> rule.getMeterId() != null).count();

        return FlowValidationResponse.builder()
                .flowId(flowId)
                .discrepancies(discrepancies)
                .asExpected(discrepancies.isEmpty())
                .pktCounts(pktCounts)
                .byteCounts(byteCounts)
                .flowRulesTotal(rulesFromDb.size())
                .switchRulesTotal(totalSwitchRules)
                .flowMetersTotal(flowMetersCount)
                .switchMetersTotal(metersCount)
                .ingressMirrorFlowIsPresent(ingressMirrorFlowIsPresent)
                .egressMirrorFlowIsPresent(egressMirrorFlowIsPresent)
                .build();
    }

    private List<SimpleSwitchRule> getSimpleSwitchRules(Flow flow, FlowPath flowPath, FlowPath oppositePath) {

        EncapsulationId encapsulationId = null;

        if (!flow.isOneSwitchFlow()) {
            Optional<? extends EncapsulationResources> encapsulationResources =
                    flowResourcesManager.getEncapsulationResources(flowPath.getPathId(), oppositePath.getPathId(),
                            flow.getEncapsulationType());
            if (encapsulationResources.isPresent()) {
                encapsulationId = encapsulationResources.get().getEncapsulation();
            } else {
                throw new IllegalStateException(
                        String.format("Encapsulation id was not found, pathId: %s", flowPath.getPathId()));
            }
        }

        return simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow, flowPath, encapsulationId,
                flowMeterMinBurstSizeInKbits, flowMeterBurstCoefficient);
    }
}
