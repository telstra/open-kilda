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

package org.openkilda.wfm.topology.flowhs.validation;

import static com.google.common.collect.Sets.newHashSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.validation.FlowValidator.EndpointDescriptor;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class FlowValidatorTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final int PORT_1 = 10;
    public static final int VLAN_1 = 11;
    public static final int VLAN_2 = 12;
    public static final EndpointDescriptor SRC_ENDPOINT = EndpointDescriptor.makeSource(
            FlowEndpoint.builder().switchId(SWITCH_ID_1).portNumber(PORT_1).build());
    public static final String FLOW_1 = "firstFlow";

    public static FlowValidator flowValidator;

    @BeforeClass
    public static void setup() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(mock(FlowRepository.class));
        when(repositoryFactory.createSwitchRepository()).thenReturn(mock(SwitchRepository.class));
        when(repositoryFactory.createIslRepository()).thenReturn(mock(IslRepository.class));
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(mock(SwitchPropertiesRepository.class));
        when(repositoryFactory.createFlowMirrorPathRepository()).thenReturn(mock(FlowMirrorPathRepository.class));
        PersistenceManager persistenceManager = mock(PersistenceManager.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        flowValidator = new FlowValidator(persistenceManager);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnFirstFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId(FLOW_1)
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(10)
                .destVlan(11)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_2)
                .destSwitch(SWITCH_ID_2)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnSecondFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId(FLOW_1)
                .srcSwitch(SWITCH_ID_2)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_1)
                .destPort(10)
                .destVlan(11)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnFirstAndSecondFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId(FLOW_1)
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .destVlan(13)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .destVlan(13)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test
    public void shouldNotFailOnSwapWhenDifferentEndpointsOnFirstAndSecondFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId(FLOW_1)
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .destVlan(13)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(14)
                .srcVlan(15)
                .destSwitch(SWITCH_ID_2)
                .destPort(16)
                .destVlan(17)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test
    public void shouldNotFailOnSpecifiedDestOuterVlansAndVlanStatistics() throws InvalidFlowException {
        RequestedFlow flow = buildFlow(0, VLAN_1, newHashSet(235));
        flowValidator.checkFlowForCorrectOuterVlansWithVlanStatistics(flow);
    }

    @Test
    public void shouldNotFailOnSpecifiedSrcOuterVlansAndVlanStatistics() throws InvalidFlowException {
        RequestedFlow flow = buildFlow(VLAN_1, 0, newHashSet(235));
        flowValidator.checkFlowForCorrectOuterVlansWithVlanStatistics(flow);
    }

    @Test
    public void shouldNotFailOnSpecifiedBothOuterVlansAndEmptyVlanStatistics() throws InvalidFlowException {
        RequestedFlow flow = buildFlow(VLAN_1, VLAN_2, new HashSet<>());
        flowValidator.checkFlowForCorrectOuterVlansWithVlanStatistics(flow);
    }

    @Test
    public void shouldNotFailOnSpecifiedBothOuterVlansAndNullVlanStatistics() throws InvalidFlowException {
        RequestedFlow flow = buildFlow(VLAN_1, VLAN_2, null);
        flowValidator.checkFlowForCorrectOuterVlansWithVlanStatistics(flow);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSpecifiedBothOuterVlansAndVlanStatistics() throws InvalidFlowException {
        RequestedFlow flow = buildFlow(VLAN_1, VLAN_2, newHashSet(235));
        flowValidator.checkFlowForCorrectOuterVlansWithVlanStatistics(flow);
    }

    @Test(expected = InvalidFlowException.class)
    public void checkForEncapsulationTypeRequirementNullTypesTest() throws InvalidFlowException {
        SwitchProperties properties = SwitchProperties.builder()
                .supportedTransitEncapsulation(null)
                .build();
        flowValidator.checkForEncapsulationTypeRequirement(SRC_ENDPOINT, properties, TRANSIT_VLAN);
    }

    @Test(expected = InvalidFlowException.class)
    public void checkForEncapsulationTypeRequirementEmptyTypesTest() throws InvalidFlowException {
        SwitchProperties properties = SwitchProperties.builder()
                .supportedTransitEncapsulation(new HashSet<>())
                .build();
        flowValidator.checkForEncapsulationTypeRequirement(SRC_ENDPOINT, properties, TRANSIT_VLAN);
    }

    @Test(expected = InvalidFlowException.class)
    public void checkForEncapsulationTypeRequirementDifferentTypeTest() throws InvalidFlowException {
        SwitchProperties properties = SwitchProperties.builder()
                .supportedTransitEncapsulation(newHashSet(VXLAN))
                .build();
        flowValidator.checkForEncapsulationTypeRequirement(SRC_ENDPOINT, properties, TRANSIT_VLAN);
    }

    @Test
    public void checkForEncapsulationTypeRequirementCorrectTypeTest() throws InvalidFlowException {
        SwitchProperties properties = SwitchProperties.builder()
                .supportedTransitEncapsulation(newHashSet(VXLAN, TRANSIT_VLAN))
                .build();
        flowValidator.checkForEncapsulationTypeRequirement(SRC_ENDPOINT, properties, TRANSIT_VLAN);
    }

    private RequestedFlow getTestRequestWithMaxLatencyAndMaxLatencyTier2(Long maxLatency, Long maxLatencyTier2) {
        return RequestedFlow.builder()
                .flowId(FLOW_1)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .destVlan(13)
                .detectConnectedDevices(new DetectConnectedDevices())
                .build();
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailIfMaxLatencyTier2HigherThanMaxLatency() throws InvalidFlowException {
        RequestedFlow flow = getTestRequestWithMaxLatencyAndMaxLatencyTier2((long) 1000, (long) 500);
        flowValidator.checkMaxLatency(flow);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailIfMaxLatencyTier2butMaxLatencyIsNull() throws InvalidFlowException {
        RequestedFlow flow = getTestRequestWithMaxLatencyAndMaxLatencyTier2(null, (long) 500);
        flowValidator.checkMaxLatency(flow);
    }

    @Test
    public void shouldNotFailIfMaxLatencyTier2andMaxLatencyAreNull() throws InvalidFlowException {
        RequestedFlow flow = getTestRequestWithMaxLatencyAndMaxLatencyTier2(null, null);
        flowValidator.checkMaxLatency(flow);
    }

    @Test
    public void shouldNotFailIfMaxLatencyTier2andMaxLatencyAreEqual() throws InvalidFlowException {
        RequestedFlow flow = getTestRequestWithMaxLatencyAndMaxLatencyTier2(500L, 500L);
        flowValidator.checkMaxLatency(flow);
    }

    private static RequestedFlow buildFlow(int srcVlan, int dstVlan, Set<Integer> statVlans) {
        return RequestedFlow.builder()
                .flowId(FLOW_1)
                .srcSwitch(SWITCH_ID_1)
                .srcPort(PORT_1)
                .srcVlan(srcVlan)
                .destSwitch(SWITCH_ID_2)
                .destPort(PORT_1)
                .destVlan(dstVlan)
                .detectConnectedDevices(new DetectConnectedDevices())
                .vlanStatistics(statVlans)
                .build();
    }
}
