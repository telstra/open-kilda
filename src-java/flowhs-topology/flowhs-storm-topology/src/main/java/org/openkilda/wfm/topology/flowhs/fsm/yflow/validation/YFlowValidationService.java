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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.validation;

import static java.util.Collections.singletonList;

import org.openkilda.messaging.command.yflow.YFlowDiscrepancyDto;
import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRuleComparator;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRuleConverter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class YFlowValidationService {
    private final YFlowRepository yFlowRepository;
    private final FlowResourcesManager flowResourcesManager;
    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    private final SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();
    private final SimpleSwitchRuleComparator simpleSwitchRuleComparator;

    public YFlowValidationService(@NonNull PersistenceManager persistenceManager,
                                  @NonNull FlowResourcesManager flowResourcesManager,
                                  long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.flowResourcesManager = flowResourcesManager;
        this.flowMeterMinBurstSizeInKbits = flowMeterMinBurstSizeInKbits;
        this.flowMeterBurstCoefficient = flowMeterBurstCoefficient;

        this.simpleSwitchRuleComparator = new SimpleSwitchRuleComparator(repositoryFactory.createSwitchRepository());
    }

    /**
     * Validate y-flow.
     */
    public YFlowDiscrepancyDto validateYFlowResources(String yFlowId,
                                                      List<SwitchFlowEntries> actualSwitchFlowEntries,
                                                      List<SwitchMeterEntries> actualSwitchMeterEntries)
            throws FlowNotFoundException, SwitchNotFoundException {

        Map<SwitchId, List<SimpleSwitchRule>> actualRules = new HashMap<>();
        for (SwitchFlowEntries switchRulesEntries : actualSwitchFlowEntries) {
            SwitchMeterEntries switchMeters = actualSwitchMeterEntries.stream()
                    .filter(meterEntries -> switchRulesEntries.getSwitchId().equals(meterEntries.getSwitchId()))
                    .findFirst()
                    .orElse(null);
            List<SimpleSwitchRule> simpleSwitchRules = simpleSwitchRuleConverter
                    .convertSwitchFlowEntriesToSimpleSwitchRules(switchRulesEntries, switchMeters, null);
            actualRules.put(switchRulesEntries.getSwitchId(), simpleSwitchRules);
        }

        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowNotFoundException(yFlowId));

        List<SimpleSwitchRule> expectedRules = new ArrayList<>();
        for (YSubFlow subFlow : yFlow.getSubFlows()) {
            Flow flow = subFlow.getFlow();
            expectedRules.addAll(buildYFlowSimpleSwitchRules(flow, yFlow.getSharedEndpoint().getSwitchId(),
                    yFlow.getSharedEndpointMeterId(),
                    flow.getForwardPathId(), flow.getReversePathId(),
                    yFlow.getYPoint(), yFlow.getMeterId()));
            if (flow.isAllocateProtectedPath()) {
                if (flow.getProtectedForwardPathId() != null && flow.getProtectedReversePathId() != null) {
                    expectedRules.addAll(buildYFlowSimpleSwitchRules(flow, yFlow.getSharedEndpoint().getSwitchId(),
                            yFlow.getSharedEndpointMeterId(),
                            flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                            yFlow.getProtectedPathYPoint(), yFlow.getProtectedPathMeterId()));
                } else {
                    log.warn("Sub-flow {} of y-flow {} has no expected protected paths", flow.getFlowId(), yFlowId);
                }
            }
        }

        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        for (SimpleSwitchRule simpleRule : expectedRules) {
            discrepancies.addAll(simpleSwitchRuleComparator.findDiscrepancy(simpleRule,
                    actualRules.get(simpleRule.getSwitchId())));
        }
        return YFlowDiscrepancyDto.builder().discrepancies(discrepancies).asExpected(discrepancies.isEmpty()).build();
    }

    private List<SimpleSwitchRule> buildYFlowSimpleSwitchRules(Flow subFlow, SwitchId sharedEndpoint,
                                                               MeterId sharedEndpointMeterId,
                                                               PathId forwardId, PathId reverseId,
                                                               SwitchId yPoint, MeterId yPointMeterId) {
        FlowPath forward = subFlow.getPath(forwardId).orElseThrow(() ->
                new IllegalStateException(String.format("Path was not found, pathId: %s", forwardId)));
        FlowPath reverse = subFlow.getPath(reverseId).orElseThrow(() ->
                new IllegalStateException(String.format("Path was not found, pathId: %s", reverseId)));
        if (reverse.getSrcSwitchId().equals(sharedEndpoint)) {
            FlowPath tmp = reverse;
            reverse = forward;
            forward = tmp;
        }
        EncapsulationId forwardEncId =
                flowResourcesManager.getEncapsulationResources(forward.getPathId(), reverse.getPathId(),
                                subFlow.getEncapsulationType()).map(EncapsulationResources::getEncapsulation)
                        .orElseThrow(() -> new IllegalStateException(
                                String.format("Encapsulation was not found, pathId: %s / %s", forwardId, reverseId)));
        EncapsulationId reverseEncId =
                flowResourcesManager.getEncapsulationResources(reverse.getPathId(), forward.getPathId(),
                                subFlow.getEncapsulationType()).map(EncapsulationResources::getEncapsulation)
                        .orElseThrow(() -> new IllegalStateException(
                                String.format("Encapsulation was not found, pathId: %s / %s", forwardId, reverseId)));

        List<SimpleSwitchRule> result = new ArrayList<>();
        if (!forward.isProtected()) {
            List<SimpleSwitchRule> ingressRules =
                    simpleSwitchRuleConverter.buildIngressSimpleSwitchRules(subFlow, forward, forwardEncId,
                            flowMeterMinBurstSizeInKbits, flowMeterBurstCoefficient);
            result.addAll(simpleSwitchRuleConverter.buildYFlowIngressSimpleSwitchRules(ingressRules,
                    sharedEndpoint, sharedEndpointMeterId));
        }

        List<PathSegment> orderedSegments = reverse.getSegments().stream()
                .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                .collect(Collectors.toList());

        for (int i = 0; i < orderedSegments.size() - 1; i++) {
            PathSegment srcPathSegment = orderedSegments.get(i);
            PathSegment dstPathSegment = orderedSegments.get(i + 1);
            if (srcPathSegment.getDestSwitchId().equals(yPoint)) {
                List<SimpleSwitchRule> yPointTransitRules = singletonList(
                        simpleSwitchRuleConverter.buildTransitSimpleSwitchRule(subFlow, reverse,
                                srcPathSegment, dstPathSegment, reverseEncId));
                result.addAll(simpleSwitchRuleConverter.buildYFlowTransitSimpleSwitchRules(yPointTransitRules,
                        yPoint, yPointMeterId, subFlow.getBandwidth(),
                        Meter.calculateBurstSize(subFlow.getBandwidth(), flowMeterMinBurstSizeInKbits,
                                flowMeterBurstCoefficient, reverse.getSrcSwitch().getDescription())));
                break;
            }
        }
        return result;
    }
}
