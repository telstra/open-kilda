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

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.HA_FLOW_ID_1;

import org.openkilda.messaging.command.haflow.HaFlowValidationResponse;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HaFlowValidationServiceTest extends HaFlowValidationTestBase {
    private static HaFlowValidationService service;

    static Switch switchA = buildSwitch(TEST_SWITCH_ID_A);
    static Switch switchB = buildSwitch(TEST_SWITCH_ID_B);
    static Switch switchC = buildSwitch(TEST_SWITCH_ID_C);
    static Switch switchD = buildSwitch(TEST_SWITCH_ID_D);
    static RuleManagerImpl ruleManagerMock = mock(RuleManagerImpl.class);
    static HaFlow haFlow = mock(HaFlow.class);
    static HaFlowPath forwardPath = mock(HaFlowPath.class);
    static HaFlowPath reversePath = mock(HaFlowPath.class);

    @BeforeAll
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        PersistenceManager persistenceManager = mock(PersistenceManager.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(switchRepository.findById(switchA.getSwitchId())).thenReturn(Optional.of(switchA));
        when(switchRepository.findById(switchB.getSwitchId())).thenReturn(Optional.of(switchB));
        when(switchRepository.findById(switchC.getSwitchId())).thenReturn(Optional.of(switchC));
        when(switchRepository.findById(switchD.getSwitchId())).thenReturn(Optional.of(switchD));
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        HaFlowRepository haFlowRepository = mock(HaFlowRepository.class);
        when(repositoryFactory.createHaFlowRepository()).thenReturn(haFlowRepository);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(haFlowRepository.findById(HA_FLOW_ID_1)).thenReturn(Optional.of(haFlow));
        when(haFlow.getForwardPath()).thenReturn(forwardPath);
        when(forwardPath.isForward()).thenReturn(true);
        when(forwardPath.isProtected()).thenReturn(false);
        when(haFlow.getReversePath()).thenReturn(reversePath);
        when(reversePath.isForward()).thenReturn(false);
        when(reversePath.isProtected()).thenReturn(false);
        when(haFlow.getPaths()).thenReturn(Arrays.asList(forwardPath, reversePath));

        service = new HaFlowValidationService(persistenceManager, ruleManagerMock);
    }

    @Test
    public void validateFlow() throws FlowNotFoundException, SwitchNotFoundException {

        when(ruleManagerMock.buildRulesHaFlowPath(eq(forwardPath), eq(false),
                any())).thenReturn(getForwardExpectedSpeakerDataList());
        when(ruleManagerMock.buildRulesHaFlowPath(eq(reversePath), eq(false),
                any())).thenReturn(getReverseExpectedSpeakerDataList());


        List<SpeakerData> floodLightSpeakerData = Stream.concat(getForwardExpectedSpeakerDataList().stream(),
                getReverseExpectedSpeakerDataList().stream()).collect(Collectors.toList());

        HaFlowValidationResponse haFlowValidationResponse = service.validateFlow(HA_FLOW_ID_1,
                Collections.singletonList(getFlowDumpResponse(filterFlowSpeakerData(floodLightSpeakerData))),
                getMeterDumpResponses(filterMeterSpeakerData(floodLightSpeakerData)),
                Collections.singletonList(getGroupDumpResponse(filterGroupSpeakerData(floodLightSpeakerData))),
                Collections.emptySet());

        Assertions.assertTrue(haFlowValidationResponse.isAsExpected(),
                "Must be true since this must be a valid flow");
        assertEquals(2, haFlowValidationResponse.getSubFlowValidationResults().size(),
                "The number of validation results and the number of sub flows must be equal");
        assertEquals(6, haFlowValidationResponse.getSubFlowValidationResults().get(0)
                .getFlowRulesTotal().intValue(), "The number of rules for the reverse flow must be equal");
        assertEquals(15, haFlowValidationResponse.getSubFlowValidationResults().get(0)
                .getSwitchRulesTotal().intValue(), "The number of rules in total must be equal");

        assertEquals(9, haFlowValidationResponse.getSubFlowValidationResults().get(1)
                .getFlowRulesTotal().intValue(), "The number of rules for the reverse flow must be equal");
        assertEquals(15,
                haFlowValidationResponse.getSubFlowValidationResults().get(1).getSwitchRulesTotal().intValue(),
                "The number of rules in total must be equal");
    }

    @Test
    public void validateCorruptedFlow() throws FlowNotFoundException, SwitchNotFoundException {
        when(ruleManagerMock.buildRulesHaFlowPath(eq(forwardPath), eq(false), any()))
                .thenReturn(getForwardExpectedSpeakerDataList());
        when(ruleManagerMock.buildRulesHaFlowPath(eq(reversePath), eq(false), any()))
                .thenReturn(getReverseExpectedSpeakerDataList());


        List<SpeakerData> floodLightSpeakerData = Stream.concat(getForwardExpectedSpeakerDataList().stream(),
                getReverseCorruptedFloodlightSpeakerDataList().stream()).collect(Collectors.toList());

        HaFlowValidationResponse haFlowValidationResponse = service.validateFlow(HA_FLOW_ID_1,
                Collections.singletonList(getFlowDumpResponse(filterFlowSpeakerData(floodLightSpeakerData))),
                getMeterDumpResponses(filterMeterSpeakerData(floodLightSpeakerData)),
                Collections.singletonList(getGroupDumpResponse(filterGroupSpeakerData(floodLightSpeakerData))),
                Collections.emptySet());

        assertFalse(haFlowValidationResponse.isAsExpected(),
                "This must be false since the forward flow is not valid");
        assertFalse(haFlowValidationResponse.getSubFlowValidationResults().get(1).getAsExpected(),
                "This must be false since the forward flow is not valid");
        assertEquals(12, haFlowValidationResponse.getSubFlowValidationResults().get(1)
                .getSwitchRulesTotal().intValue(), "The number of rules in total must be equal");
        assertEquals(9, haFlowValidationResponse.getSubFlowValidationResults().get(1)
                .getFlowRulesTotal().intValue(), "The number of rules for the forward flow must be equal");
        assertEquals(1, haFlowValidationResponse.getSubFlowValidationResults().get(1)
                .getSwitchMetersTotal().intValue(), "The number of meters must be");
        assertEquals("all", haFlowValidationResponse.getSubFlowValidationResults().get(1)
                        .getDiscrepancies().get(0).getField(),
                "There must be 'all' keyword for the rule that is exist only in RuleManager and"
                        + " not in FloodLight");
        assertEquals("all", haFlowValidationResponse.getSubFlowValidationResults().get(1)
                        .getDiscrepancies().get(1).getField(),
                "There must be 'all' keyword for the rule that is exist only in RuleManager and"
                        + " not in FloodLight");
        assertEquals("all", haFlowValidationResponse.getSubFlowValidationResults().get(1)
                        .getDiscrepancies().get(2).getField(),
                "There must be 'all' keyword for the rule that is exist only in RuleManager and"
                        + " not in FloodLight");
        assertEquals("outPort", haFlowValidationResponse.getSubFlowValidationResults().get(1)
                .getDiscrepancies().get(3).getField(), "There must be the 'outPort' discrepancy");
        assertEquals("outVlan", haFlowValidationResponse.getSubFlowValidationResults().get(1)
                .getDiscrepancies().get(4).getField(), "There must be the 'outVlan' discrepancy");
    }

    @Test
    public void validateCheckFlowStatusAndGetFlowUsingNotExistingFlow() {
        assertThrows(FlowNotFoundException.class, () -> {
            service.findValidHaFlowById("test");
        });
    }

    @Test
    public void validateCheckFlowStatusAndGetFlowWithDownStatus() {
        assertThrows(IllegalFlowStateException.class, () -> {
            when(haFlow.getStatus()).thenReturn(FlowStatus.DOWN);
            service.findValidHaFlowById(HA_FLOW_ID_1);
        });
    }

    @Test
    public void validateFlowUsingNotExistingFlow() {
        assertThrows(FlowNotFoundException.class, () ->
                service.validateFlow("test", emptyList(), emptyList(), emptyList(), Sets.newHashSet()));
    }
}
