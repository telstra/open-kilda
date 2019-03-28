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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.messaging.nbtopology.response.PathDiscrepancyEntity;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.FlowNotFoundException;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FlowValidationServiceTest extends FlowValidationTestBase {
    private static FlowValidationService service;

    @BeforeClass
    public static void setUpOnce() {
        FlowValidationTestBase.setUpOnce();
        service = new FlowValidationService(persistenceManager, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);
    }

    @Test
    public void shouldGetSwitchIdListByFlowId() throws FlowNotFoundException {
        buildFlowA();
        List<SwitchId> switchIds = service.getSwitchIdListByFlowId(TEST_FLOW_ID_A);
        assertEquals(3, switchIds.size());

        buildOneSwitchPortFlowB();
        switchIds = service.getSwitchIdListByFlowId(TEST_FLOW_ID_B);
        assertEquals(1, switchIds.size());
    }

    @Test(expected = FlowNotFoundException.class)
    public void shouldNotGetSwitchIdListUsingNotExistingFlow() throws FlowNotFoundException {
        service.getSwitchIdListByFlowId(TEST_FLOW_ID_A);
    }

    @Test
    public void shouldValidateFlow() throws FlowNotFoundException {
        buildFlowA();
        List<FlowValidationResponse> result = service.validateFlow(TEST_FLOW_ID_A, getSwitchFlowEntriesFlowA(),
                getSwitchMeterEntriesFlowA());
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
        assertEquals(6, (int) result.get(0).getSwitchRulesTotal());
        assertEquals(6, (int) result.get(1).getSwitchRulesTotal());

        result = service.validateFlow(TEST_FLOW_ID_A, getWrongSwitchFlowEntriesFlowA(),
                getWrongSwitchMeterEntriesFlowA());
        assertEquals(2, result.size());
        assertEquals(6, result.get(0).getDiscrepancies().size());
        assertEquals(3, result.get(1).getDiscrepancies().size());

        List<String> forwardDiscrepancies = result.get(0).getDiscrepancies().stream()
                .map(PathDiscrepancyEntity::getField)
                .collect(Collectors.toList());
        assertTrue(forwardDiscrepancies.contains("cookie"));
        assertTrue(forwardDiscrepancies.contains("inVlan"));
        assertTrue(forwardDiscrepancies.contains("outVlan"));
        assertTrue(forwardDiscrepancies.contains("meterRate"));
        assertTrue(forwardDiscrepancies.contains("meterBurstSize"));
        assertTrue(forwardDiscrepancies.contains("meterFlags"));

        List<String> reverseDiscrepancies = result.get(1).getDiscrepancies().stream()
                .map(PathDiscrepancyEntity::getField)
                .collect(Collectors.toList());
        assertTrue(reverseDiscrepancies.contains("inPort"));
        assertTrue(reverseDiscrepancies.contains("outPort"));
        assertTrue(reverseDiscrepancies.contains("meterId"));

        buildOneSwitchPortFlowB();
        result = service.validateFlow(TEST_FLOW_ID_B, getSwitchFlowEntriesFlowB(), getSwitchMeterEntriesFlowB());
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
    }

    @Test(expected = FlowNotFoundException.class)
    public void shouldValidateFlowUsingNotExistingFlow() throws FlowNotFoundException {
        service.validateFlow("test", new ArrayList<>(), new ArrayList<>());
    }
}
