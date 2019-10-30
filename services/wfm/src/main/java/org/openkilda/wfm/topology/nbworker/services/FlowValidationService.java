/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.messaging.nbtopology.response.PathDiscrepancyEntity;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.rule.validation.SimpleSwitchRule;
import org.openkilda.wfm.share.utils.rule.validation.SimpleSwitchRuleConverter;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class FlowValidationService {
    private SwitchRepository switchRepository;
    private FlowRepository flowRepository;
    private FlowResourcesManager flowResourcesManager;

    private SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();

    public FlowValidationService(PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
    }

    /**
     * Check the flow status.
     */
    public void checkFlowStatus(String flowId) throws FlowNotFoundException, IllegalFlowStateException {
        Flow flow = flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));
        if (FlowStatus.DOWN.equals(flow.getStatus())) {
            throw new IllegalFlowStateException(flowId);
        }
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
    public List<FlowValidationResponse> validateFlow(String flowId, List<SwitchFlowEntries> switchFlowEntries)
            throws FlowNotFoundException {

        Map<SwitchId, List<SimpleSwitchRule>> switchRules = new HashMap<>();
        int rulesCount = 0;
        for (SwitchFlowEntries switchEntries : switchFlowEntries) {
            switchRules.put(switchEntries.getSwitchId(),
                    simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchEntries));
            rulesCount += Optional.ofNullable(switchEntries.getFlowEntries())
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
        flowValidationResponse.add(compareRules(switchRules, forwardRules, flowId, rulesCount));

        List<SimpleSwitchRule> reverseRules = getSimpleSwitchRules(flow, flow.getReversePath(), flow.getForwardPath());
        flowValidationResponse.add(compareRules(switchRules, reverseRules, flowId, rulesCount));

        if (flow.getProtectedForwardPath() != null) {
            List<SimpleSwitchRule> forwardProtectedRules = getSimpleSwitchRules(flow, flow.getProtectedForwardPath(),
                    flow.getProtectedReversePath());
            flowValidationResponse.add(compareRules(switchRules, forwardProtectedRules, flowId, rulesCount));
        }

        if (flow.getProtectedReversePath() != null) {
            List<SimpleSwitchRule> reverseProtectedRules = getSimpleSwitchRules(flow, flow.getProtectedReversePath(),
                    flow.getProtectedForwardPath());
            flowValidationResponse.add(compareRules(switchRules, reverseProtectedRules, flowId, rulesCount));
        }

        return flowValidationResponse;
    }

    private FlowValidationResponse compareRules(Map<SwitchId, List<SimpleSwitchRule>> rulesPerSwitch,
                                                List<SimpleSwitchRule> rulesFromDb,
                                                String flowId, int totalSwitchRules) {

        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        List<Long> pktCounts = new ArrayList<>();
        List<Long> byteCounts = new ArrayList<>();
        rulesFromDb.forEach(simpleRule -> discrepancies.addAll(
                findDiscrepancy(simpleRule, rulesPerSwitch.get(simpleRule.getSwitchId()), pktCounts, byteCounts)));

        return FlowValidationResponse.builder()
                .flowId(flowId)
                .discrepancies(discrepancies)
                .asExpected(discrepancies.isEmpty())
                .pktCounts(pktCounts)
                .byteCounts(byteCounts)
                .flowRulesTotal(rulesFromDb.size())
                .switchRulesTotal(totalSwitchRules)
                .build();
    }

    private List<SimpleSwitchRule> getSimpleSwitchRules(Flow flow, FlowPath flowPath, FlowPath oppositePath) {

        EncapsulationId encapsulationId = null;

        if (!flow.isOneSwitchFlow()) {
            Optional<EncapsulationResources> encapsulationResources =
                    flowResourcesManager.getEncapsulationResources(flowPath.getPathId(), oppositePath.getPathId(),
                            flow.getEncapsulationType());
            if (encapsulationResources.isPresent()) {
                encapsulationId = encapsulationResources.get().getEncapsulation();
            } else {
                throw new IllegalStateException(
                        String.format("Encapsulation id was not found, pathId: %s", flowPath.getPathId()));
            }
        }

        return simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow, flowPath, encapsulationId);
    }

    private List<PathDiscrepancyEntity> findDiscrepancy(SimpleSwitchRule expected, List<SimpleSwitchRule> actual,
                                                        List<Long> pktCounts, List<Long> byteCounts) {
        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        SimpleSwitchRule matched = findMatched(expected, actual);

        if (matched == null) {
            discrepancies.add(new PathDiscrepancyEntity(String.valueOf(expected), "all", String.valueOf(expected), ""));
            pktCounts.add(-1L);
            byteCounts.add(-1L);
        } else {
            discrepancies.addAll(getDiscrepancies(expected, matched));
            pktCounts.add(matched.getPktCount());
            byteCounts.add(matched.getByteCount());
        }

        return discrepancies;
    }

    private SimpleSwitchRule findMatched(SimpleSwitchRule expected, List<SimpleSwitchRule> actual) {

        //try to match on the cookie
        SimpleSwitchRule matched = actual.stream()
                .filter(rule -> rule.getCookie() != 0 && rule.getCookie() == expected.getCookie())
                .findFirst()
                .orElse(null);

        //if no cookie match, then try to match on in_port and in_vlan
        if (matched == null) {
            matched = actual.stream()
                    .filter(rule -> rule.getInPort() == expected.getInPort()
                            && rule.getInVlan() == expected.getInVlan())
                    .findFirst()
                    .orElse(null);
        }

        //if cookie or in_port and in_vlan doesn't match, try to match on out_port and out_vlan
        if (matched == null) {
            matched = actual.stream()
                    .filter(rule -> rule.getOutPort() == expected.getOutPort()
                            && rule.getOutVlan() == expected.getOutVlan())
                    .findFirst()
                    .orElse(null);
        }
        return matched;
    }

    private List<PathDiscrepancyEntity> getDiscrepancies(SimpleSwitchRule expected, SimpleSwitchRule matched) {
        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        if (matched.getCookie() != expected.getCookie()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "cookie",
                    String.valueOf(expected.getCookie()), String.valueOf(matched.getCookie())));
        }
        if (matched.getInPort() != expected.getInPort()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "inPort",
                    String.valueOf(expected.getInPort()), String.valueOf(matched.getInPort())));
        }
        if (matched.getInVlan() != expected.getInVlan()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "inVlan",
                    String.valueOf(expected.getInVlan()), String.valueOf(matched.getInVlan())));
        }
        if (matched.getTunnelId() != expected.getTunnelId()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "tunnelId",
                    String.valueOf(expected.getTunnelId()), String.valueOf(matched.getTunnelId())));
        }
        if (matched.getOutPort() != expected.getOutPort()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "outPort",
                    String.valueOf(expected.getOutPort()), String.valueOf(matched.getOutPort())));
        }
        if (matched.getOutVlan() != expected.getOutVlan()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "outVlan",
                    String.valueOf(expected.getOutVlan()), String.valueOf(matched.getOutVlan())));
        }
        //meters on OF_12 switches are not supported, so skip them.
        if ((matched.getVersion() == null || matched.getVersion().compareTo("OF_12") > 0)
                && !Objects.equals(matched.getMeterId(), expected.getMeterId())) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterId",
                    String.valueOf(expected.getMeterId()), String.valueOf(matched.getMeterId())));
        }
        return discrepancies;
    }
}
