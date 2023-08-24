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

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FlowValidationServiceTest extends FlowValidationTestBase {
    private static FlowValidationService service;


    @BeforeAll
    public static void setUpOnce() {
        FlowValidationTestBase.setUpOnce();
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        service = new FlowValidationService(persistenceManager,
                new RuleManagerImpl(ruleManagerConfig));
    }

    @Test
    public void validateGetSwitchIdListByFlowId() {
        buildTransitVlanFlow("");
        List<SwitchId> switchIds = service.getSwitchIdListByFlowId(TEST_FLOW_ID_A);
        assertEquals(4, switchIds.size());

        buildOneSwitchPortFlow();
        switchIds = service.getSwitchIdListByFlowId(TEST_FLOW_ID_B);
        assertEquals(1, switchIds.size());
    }

    @Test
    public void validateFlowWithTransitVlanEncapsulation() throws FlowNotFoundException, SwitchNotFoundException {
        buildTransitVlanFlow("");
        validateFlow(true);
    }

    @Test
    public void validateFlowWithVxlanEncapsulation() throws FlowNotFoundException, SwitchNotFoundException {
        buildVxlanFlow();
        validateFlow(false);
    }

    private void validateFlow(boolean isTransitVlan)
            throws FlowNotFoundException, SwitchNotFoundException {

        List<FlowDumpResponse> flowEntries =
                isTransitVlan ? getFlowDumpResponseWithTransitVlan() : getSwitchFlowEntriesWithVxlan();
        List<MeterDumpResponse> meterEntries = getMeterDumpResponses();

        Set<SwitchId> switchIdSet = Sets.newHashSet(service.getSwitchIdListByFlowId(TEST_FLOW_ID_A));
        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_A, flowEntries, meterEntries,
                emptyList(), switchIdSet);
        assertEquals(4, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
        assertEquals(0, result.get(2).getDiscrepancies().size());
        assertEquals(0, result.get(3).getDiscrepancies().size());
        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
        assertEquals(2, (int) result.get(2).getFlowRulesTotal());
        assertEquals(2, (int) result.get(3).getFlowRulesTotal());
        assertEquals(10, (int) result.get(0).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(1).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(2).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(3).getSwitchRulesTotal());

        flowEntries =
                isTransitVlan ? getWrongSwitchFlowEntriesWithTransitVlan() : getWrongSwitchFlowEntriesWithVxlan();
        meterEntries = getWrongSwitchMeterEntries();
        result = service.validateFlow(TEST_FLOW_ID_A, flowEntries, meterEntries, emptyList(), switchIdSet);
        assertEquals(4, result.size());
        assertEquals(3, result.get(0).getDiscrepancies().size());
        assertEquals(3, result.get(1).getDiscrepancies().size());
        assertEquals(2, result.get(2).getDiscrepancies().size());
        assertEquals(2, result.get(3).getDiscrepancies().size());

        List<String> forwardDiscrepancies = result.get(0).getDiscrepancies().stream()
                .map(PathDiscrepancyEntity::getField)
                .collect(Collectors.toList());
        Assertions.assertTrue(forwardDiscrepancies.contains("all"));
        Assertions.assertTrue(forwardDiscrepancies.contains(isTransitVlan ? "inVlan" : "tunnelId"));
        Assertions.assertTrue(forwardDiscrepancies.contains("outVlan"));

        List<String> reverseDiscrepancies = result.get(1).getDiscrepancies().stream()
                .map(PathDiscrepancyEntity::getField)
                .collect(Collectors.toList());
        Assertions.assertTrue(reverseDiscrepancies.contains("inPort"));
        Assertions.assertTrue(reverseDiscrepancies.contains("outPort"));
        Assertions.assertTrue(reverseDiscrepancies.contains("meterId"));

        List<String> protectedForwardDiscrepancies = result.get(2).getDiscrepancies().stream()
                .map(PathDiscrepancyEntity::getField)
                .collect(Collectors.toList());
        Assertions.assertTrue(protectedForwardDiscrepancies.contains(isTransitVlan ? "inVlan" : "tunnelId"));
        Assertions.assertTrue(protectedForwardDiscrepancies.contains("outVlan"));

        List<String> protectedReverseDiscrepancies = result.get(3).getDiscrepancies().stream()
                .map(PathDiscrepancyEntity::getField)
                .collect(Collectors.toList());
        Assertions.assertTrue(protectedReverseDiscrepancies.contains("outPort"));
        Assertions.assertTrue(protectedReverseDiscrepancies.contains("inPort"));
    }

    @Test
    public void validateOneSwitchFlow() throws FlowNotFoundException, SwitchNotFoundException {
        buildOneSwitchPortFlow();
        List<FlowDumpResponse> switchEntries = getSwitchFlowEntriesOneSwitchFlow();
        List<MeterDumpResponse> meterEntries = getSwitchMeterEntriesOneSwitchFlow();

        Set<SwitchId> switchIdSet = Sets.newHashSet(service.getSwitchIdListByFlowId(TEST_FLOW_ID_B));

        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_B, switchEntries, meterEntries,
                emptyList(), switchIdSet);
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
    }

    @Test
    public void validateFlowUsingNotExistingFlow() {
        assertThrows(FlowNotFoundException.class, () ->
                service.validateFlow("test", emptyList(), emptyList(), emptyList(), Sets.newHashSet()));
    }

    @Test
    public void validateFlowWithTransitVlanEncapsulationESwitch()
            throws FlowNotFoundException, SwitchNotFoundException {
        buildTransitVlanFlow("E");

        List<FlowDumpResponse> flowEntries = getFlowDumpResponseWithTransitVlan();
        List<MeterDumpResponse> meterEntries = getSwitchMeterEntriesWithESwitch();

        Set<SwitchId> switchIdSet = Sets.newHashSet(service.getSwitchIdListByFlowId(TEST_FLOW_ID_A));
        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_A, flowEntries, meterEntries,
                emptyList(), switchIdSet);
        assertEquals(4, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
        assertEquals(0, result.get(2).getDiscrepancies().size());
        assertEquals(0, result.get(3).getDiscrepancies().size());
        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
        assertEquals(2, (int) result.get(2).getFlowRulesTotal());
        assertEquals(2, (int) result.get(3).getFlowRulesTotal());
        assertEquals(10, (int) result.get(0).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(1).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(2).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(3).getSwitchRulesTotal());
    }
}
