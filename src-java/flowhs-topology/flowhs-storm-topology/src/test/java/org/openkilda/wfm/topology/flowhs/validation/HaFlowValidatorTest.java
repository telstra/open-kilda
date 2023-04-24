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

package org.openkilda.wfm.topology.flowhs.validation;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

public class HaFlowValidatorTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final int PORT_1 = 101;
    public static final int PORT_2 = 102;
    public static final int PORT_3 = 103;
    public static final int VLAN_1 = 1;
    public static final int VLAN_2 = 2;
    public static final int VLAN_3 = 3;
    public static final int INNER_VLAN_1 = 1;
    public static final int INNER_VLAN_2 = 2;
    public static final int INNER_VLAN_3 = 3;
    public static final String HA_FLOW_ID = "test";
    public static final String SUB_FLOW_ID_1 = "test_1";
    public static final String SUB_FLOW_ID_2 = "test_2";

    public static HaFlowValidator haFlowValidator;

    @BeforeClass
    public static void setup() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(mock(FlowRepository.class));
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(mock(IslRepository.class));
        SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);
        when(repositoryFactory.createFlowMirrorPathRepository()).thenReturn(mock(FlowMirrorPathRepository.class));
        when(repositoryFactory.createHaFlowRepository()).thenReturn(mock(HaFlowRepository.class));
        PhysicalPortRepository physicalPortRepository = mock(PhysicalPortRepository.class);
        when(repositoryFactory.createPhysicalPortRepository()).thenReturn(physicalPortRepository);
        PersistenceManager persistenceManager = mock(PersistenceManager.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        haFlowValidator = new HaFlowValidator(persistenceManager);

        when(switchRepository.findById(SWITCH_ID_1))
                .thenReturn(Optional.of(Switch.builder().switchId(SWITCH_ID_1).ofDescriptionSoftware("")
                        .status(SwitchStatus.ACTIVE).build()));
        when(switchPropertiesRepository.findBySwitchId(SWITCH_ID_1))
                .thenReturn(Optional.of(SwitchProperties.builder().multiTable(true).build()));
        when(switchRepository.findById(SWITCH_ID_2))
                .thenReturn(Optional.of(Switch.builder().switchId(SWITCH_ID_2).ofDescriptionSoftware("")
                        .status(SwitchStatus.ACTIVE).build()));
        when(switchPropertiesRepository.findBySwitchId(SWITCH_ID_2))
                .thenReturn(Optional.of(SwitchProperties.builder().multiTable(true).build()));
        when(switchRepository.findById(SWITCH_ID_3))
                .thenReturn(Optional.of(Switch.builder().switchId(SWITCH_ID_3).ofDescriptionSoftware("")
                        .status(SwitchStatus.ACTIVE).build()));
        when(switchPropertiesRepository.findBySwitchId(SWITCH_ID_3))
                .thenReturn(Optional.of(SwitchProperties.builder().multiTable(true).build()));

        when(physicalPortRepository.findBySwitchIdAndPortNumber(any(), anyInt())).thenReturn(Optional.empty());
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfNoSubFlowsProvidedTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                .subFlows(emptyList())
                .build();
        haFlowValidator.validate(request);
    }

    @Test
    public void passIfSubFlow1IsOneSwitchFlowTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                .subFlows(Arrays.asList(
                        buildSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_2, VLAN_1, INNER_VLAN_1),
                        buildSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2)))
                .build();
        haFlowValidator.validate(request);
    }

    @Test
    public void passIfSubFlow2IsOneSwitchFlowTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1))
                .subFlows(Arrays.asList(
                        buildSubFlow(SUB_FLOW_ID_1, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2),
                        buildSubFlow(SUB_FLOW_ID_2, SWITCH_ID_1, PORT_2, VLAN_2, INNER_VLAN_2)))
                .build();
        haFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void faitIfBothSubFlowIsOneSwitchFlowTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_3))
                .subFlows(Arrays.asList(
                        buildSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1),
                        buildSubFlow(SUB_FLOW_ID_2, SWITCH_ID_1, PORT_2, VLAN_2, INNER_VLAN_2)))
                .build();
        haFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfNegativeBandwidthProvidedTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .maximumBandwidth(-1)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                .subFlows(Arrays.asList(
                        buildSubFlow(SUB_FLOW_ID_1, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2),
                        buildSubFlow(SUB_FLOW_ID_2, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3)))
                .build();
        haFlowValidator.validate(request);
    }


    @Test(expected = InvalidFlowException.class)
    public void failIfMaxLatencyTier2HigherThanMaxLatencyTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = buildHaFlowRequestWithMaxLatency(1000L, 500L);
        haFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfMaxLatencyTier2butMaxLatencyIsNullTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = buildHaFlowRequestWithMaxLatency(null, 500L);
        haFlowValidator.validate(request);
    }

    @Test
    public void passIfMaxLatencyTier2butMaxLatencyIsNullTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = buildHaFlowRequestWithMaxLatency(null, null);
        haFlowValidator.validate(request);
    }

    @Test
    public void passIfMaxLatencyTier2EqualToMaxLatencyTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = buildHaFlowRequestWithMaxLatency(500L, 500L);
        haFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfNoSharedEndpointProvidedTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .subFlows(Arrays.asList(
                        buildSubFlow(SUB_FLOW_ID_1, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2),
                        buildSubFlow(SUB_FLOW_ID_2, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3)))
                .build();
        haFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfSubFlowHasNoEndpointProvidedTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                .subFlows(Arrays.asList(
                        HaSubFlowDto.builder().flowId(SUB_FLOW_ID_1).build(),
                        HaSubFlowDto.builder().flowId(SUB_FLOW_ID_2).build()))
                .build();
        haFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfSubFlowHasNoIdTest()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        HaFlowRequest request = HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                .subFlows(Arrays.asList(HaSubFlowDto.builder()
                                .endpoint(new FlowEndpoint(SWITCH_ID_2, PORT_2, VLAN_2))
                                .build(),
                        HaSubFlowDto.builder()
                                .endpoint(new FlowEndpoint(SWITCH_ID_3, PORT_3, VLAN_3))
                                .build()))
                .build();
        haFlowValidator.validate(request);
    }

    private static HaSubFlowDto buildSubFlow(String flowId, SwitchId switchId, int port, int vlan, int innerVlan) {
        return HaSubFlowDto.builder()
                .flowId(flowId)
                .endpoint(new FlowEndpoint(switchId, port, vlan, innerVlan))
                .build();
    }

    private static HaFlowRequest buildHaFlowRequestWithMaxLatency(Long maxLatency, Long maxLatencyTier2) {
        return HaFlowRequest.builder()
                .haFlowId(HA_FLOW_ID)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .sharedEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                .subFlows(Arrays.asList(
                        buildSubFlow(SUB_FLOW_ID_1, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2),
                        buildSubFlow(SUB_FLOW_ID_2, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3)))
                .build();
    }
}
