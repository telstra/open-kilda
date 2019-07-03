/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.validation;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.TestFlowBuilder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Optional;

public class FlowValidatorTest {
    private FlowValidator target;

    private static final SwitchId SRC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:01");
    private static final int SRC_PORT = 1;
    private static final int SRC_VLAN = 1;
    private static final SwitchId DST_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int DST_PORT = 5;
    private static final int DST_VLAN = 5;
    private static final SwitchId FAIL_SRC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId FAIL_DST_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:04");
    private static final String FLOW_ID = "test_flow";
    private static final String ANOTHER_FLOW_ID = "another_test_flow";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private void defaultSetUp(SwitchId srcSwitchId, int srtPort, int srcVlan,
                              SwitchId dstSwitchId, int dstPort, int dstVlan, String flowId) {
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        Switch srcSwitch = Switch.builder().switchId(srcSwitchId).build();
        Switch dstSwitch = Switch.builder().switchId(dstSwitchId).build();
        when(switchRepository.findById(eq(srcSwitchId))).thenReturn(
                Optional.of(srcSwitch));
        when(switchRepository.findById(eq(dstSwitchId))).thenReturn(
                Optional.of(dstSwitch));

        IslRepository islRepository = mock(IslRepository.class);
        Isl isl = Isl.builder().srcSwitch(srcSwitch).srcPort(srtPort).destSwitch(dstSwitch).destPort(dstPort).build();
        when(islRepository.findByEndpoint(eq(srcSwitchId), eq(srtPort))).thenReturn(singletonList(isl));
        when(islRepository.findByEndpoint(eq(dstSwitchId), eq(dstPort))).thenReturn(singletonList(isl));

        UnidirectionalFlow flow = new TestFlowBuilder(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srtPort)
                .srcVlan(srcVlan)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .destVlan(dstVlan)
                .buildUnidirectionalFlow();

        FlowRepository flowRepository = mock(FlowRepository.class);
        when(flowRepository.findByEndpoint(eq(srcSwitchId), eq(srtPort)))
                .thenReturn(singletonList(flow.getFlow()));
        when(flowRepository.findByEndpoint(eq(dstSwitchId), eq(dstPort)))
                .thenReturn(singletonList(flow.getFlow()));

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);

        target = new FlowValidator(repositoryFactory);
    }

    private void setUpWithValidateSwitch(SwitchId srcSwitchId, SwitchId dstSwitchId) {
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(switchRepository.exists(eq(srcSwitchId))).thenReturn(true);
        when(switchRepository.exists(eq(dstSwitchId))).thenReturn(true);

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        target = new FlowValidator(repositoryFactory);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourcePortIsOccupiedByIsl() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT + 1)
                .buildUnidirectionalFlow();

        target.checkFlowForIslConflicts(flow.getFlow());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationPortIsOccupiedByIsl() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .buildUnidirectionalFlow();

        target.checkFlowForIslConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfPortIsNotOccupiedByIsl() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT + 1)
                .buildUnidirectionalFlow();

        target.checkFlowForIslConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfSourceVlanIsZeroAndPortVlanIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(dstSwitch)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourcePortIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(0)
                .destSwitch(dstSwitch)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnFirstFlow() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 1, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();
        Flow firstFlow = new TestFlowBuilder()
                .flowId("firstFlow")
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(srcSwitch)
                .destPort(SRC_PORT)
                .destVlan(SRC_VLAN)
                .build();

        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();

        target.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnSecondFlow() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 1, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();
        Flow firstFlow = new TestFlowBuilder()
                .flowId("firstFlow")
                .srcSwitch(otherSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(otherSwitch)
                .destPort(DST_PORT)
                .build();

        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(srcSwitch)
                .destPort(SRC_PORT)
                .destVlan(SRC_VLAN)
                .build();

        target.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnFirstAndSecondFlow() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 1, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();
        Flow firstFlow = new TestFlowBuilder()
                .flowId("firstFlow")
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(otherSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN)
                .build();

        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(otherSwitch)
                .destPort(SRC_PORT)
                .destVlan(SRC_VLAN)
                .build();

        target.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(dstSwitch)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfSourceVlanIsOccupiedAndRequestedSrcVlanIsZero() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 10, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .destSwitch(dstSwitch)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN + 1)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfDestinationVlanNotZeroAndPortIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_PORT, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(dstSwitch)
                .destPort(0)
                .destVlan(DST_VLAN)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfPortIsOccupiedAndDestinationVlanNotZero() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationPortIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .destVlan(0)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfDestinationVlanIsNotOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN + 1)
                .buildUnidirectionalFlow();
        target.checkFlowForEndpointConflicts(flow.getFlow());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailForNegativeBandwidth() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .bandwidth(-1)
                .buildUnidirectionalFlow();
        target.checkBandwidth(flow.getFlow());
    }

    @Test
    public void shouldNotFailOnCheckSwitches() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .buildUnidirectionalFlow();
        target.checkSwitchesExists(flow.getFlow());
    }

    @Test
    public void shouldNotFailOnSingleSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, SRC_SWITCH_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .destSwitch(srcSwitch)
                .buildUnidirectionalFlow();
        target.checkSwitchesExists(flow.getFlow());
    }

    @Test
    public void shouldFailOnSourceSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch failSrcSwitch = Switch.builder().switchId(FAIL_SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(failSrcSwitch)
                .destSwitch(dstSwitch)
                .buildUnidirectionalFlow();

        String expectedMessage = String.format("Source switch %s is not connected to the controller",
                FAIL_SRC_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);
        target.checkSwitchesExists(flow.getFlow());
    }

    @Test
    public void shouldFailOnDestinationSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch failDestSwitch = Switch.builder().switchId(FAIL_DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .destSwitch(failDestSwitch)
                .buildUnidirectionalFlow();

        String expectedMessage =
                String.format("Destination switch %s is not connected to the controller", FAIL_DST_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow.getFlow());
    }

    @Test
    public void shouldFailOnSourceAndDestinationSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch failSrcSwitch = Switch.builder().switchId(FAIL_SRC_SWITCH_ID).build();
        Switch failDestSwitch = Switch.builder().switchId(FAIL_DST_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(failSrcSwitch)
                .destSwitch(failDestSwitch)
                .buildUnidirectionalFlow();

        String expectedMessage =
                String.format("Source switch %s and Destination switch %s are not connected to the controller",
                        FAIL_SRC_SWITCH_ID, FAIL_DST_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow.getFlow());
    }

    @Test
    public void shouldFailOnOneSwitchFlowWithEqualPortsAndVlans() throws SwitchValidationException {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(srcSwitch)
                .destPort(SRC_PORT)
                .destVlan(SRC_VLAN)
                .buildUnidirectionalFlow();

        String expectedMessage = "It is not allowed to create one-switch flow for the same ports and vlans";

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkOneSwitchFlowHasNoConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualPortsButDifferentVlans() throws SwitchValidationException {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(srcSwitch)
                .destPort(SRC_PORT)
                .destVlan(DST_VLAN)
                .buildUnidirectionalFlow();
        target.checkOneSwitchFlowHasNoConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualVlansButDifferentPorts() throws SwitchValidationException {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(srcSwitch)
                .destPort(DST_PORT)
                .destVlan(SRC_VLAN)
                .buildUnidirectionalFlow();

        target.checkOneSwitchFlowHasNoConflicts(flow.getFlow());
    }

    @Test
    public void shouldNotFailIfExistentFlowIsDefaultOnSrc() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("ff:ff")).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();

        Flow firstFlow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(1)
                .destSwitch(dstSwitch)
                .build();
        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();
        target.checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
    }

    @Test
    public void shouldNotFailIfExistentFlowIsDefaultOnDst() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();

        Flow firstFlow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN + 1)
                .destSwitch(otherSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN)
                .build();
        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();
        target.checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
    }

    @Test(expected = ValidationException.class)
    public void shouldFailIfBothExistentAndSwappingFlowsAreDefault() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();

        Flow firstFlow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(0)
                .destSwitch(otherSwitch)
                .destPort(DST_PORT)
                .destVlan(0)
                .build();
        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();
        target.checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
    }

    @Test(expected = ValidationException.class)
    public void shouldFailIfConflictOnSourceEndpoints() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();

        Flow firstFlow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .srcPort(SRC_PORT)
                .srcVlan(SRC_VLAN)
                .destSwitch(otherSwitch)
                .build();
        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();
        target.checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
    }

    @Test(expected = ValidationException.class)
    public void shouldFailIfConflictOnDestinationEndpoints() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();

        Flow firstFlow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .destPort(DST_PORT)
                .destVlan(DST_VLAN)
                .build();
        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();
        target.checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
    }

    @Test(expected = ValidationException.class)
    public void shouldFailIfConflictOnDifferentEndpoints() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();
        Switch otherSwitch = Switch.builder().switchId(new SwitchId("ff:fe")).build();

        Flow firstFlow = new TestFlowBuilder()
                .flowId(ANOTHER_FLOW_ID)
                .srcSwitch(dstSwitch)
                .destSwitch(srcSwitch)
                .destPort(SRC_PORT)
                .destVlan(SRC_VLAN)
                .build();
        Flow secondFlow = new TestFlowBuilder()
                .flowId("secondFlow")
                .srcSwitch(otherSwitch)
                .destSwitch(otherSwitch)
                .build();
        target.checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
    }

}
