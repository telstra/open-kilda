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

import com.google.common.collect.Sets;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;

public class FlowValidatorTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final int PORT_1 = 10;
    public static final EndpointDescriptor SRC_ENDPOINT = EndpointDescriptor.makeSource(
            FlowEndpoint.builder().switchId(SWITCH_ID_1).portNumber(PORT_1).build());

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
                .flowId("firstFlow")
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
                .flowId("firstFlow")
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
                .flowId("firstFlow")
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
                .flowId("firstFlow")
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
                .supportedTransitEncapsulation(Sets.newHashSet(VXLAN))
                .build();
        flowValidator.checkForEncapsulationTypeRequirement(SRC_ENDPOINT, properties, TRANSIT_VLAN);
    }

    @Test
    public void checkForEncapsulationTypeRequirementCorrectTypeTest() throws InvalidFlowException {
        SwitchProperties properties = SwitchProperties.builder()
                .supportedTransitEncapsulation(Sets.newHashSet(VXLAN, TRANSIT_VLAN))
                .build();
        flowValidator.checkForEncapsulationTypeRequirement(SRC_ENDPOINT, properties, TRANSIT_VLAN);
    }
}
