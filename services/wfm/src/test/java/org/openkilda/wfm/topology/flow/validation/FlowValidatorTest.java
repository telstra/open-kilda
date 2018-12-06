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
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
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

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        SwitchRepository switchRepository = mock(SwitchRepository.class);

        when(switchRepository.findById(eq(SRC_SWITCH_ID))).thenReturn(Optional.of(new Switch()));
        when(switchRepository.findById(eq(DST_SWITCH_ID))).thenReturn(Optional.of(new Switch()));
        when(switchRepository.exists(eq(SRC_SWITCH_ID))).thenReturn(true);
        when(switchRepository.exists(eq(DST_SWITCH_ID))).thenReturn(true);
        when(switchRepository.findById(eq(SRC_SWITCH_ID))).thenReturn(
                Optional.of(Switch.builder().switchId(SRC_SWITCH_ID).build()));
        when(switchRepository.findById(eq(DST_SWITCH_ID))).thenReturn(
                Optional.of(Switch.builder().switchId(DST_SWITCH_ID).build()));

        FlowRepository flowRepository = mock(FlowRepository.class);
        Flow flow = new Flow();
        flow.setFlowId("test_flow");
        flow.setSrcVlan(SRC_VLAN);
        flow.setDestVlan(DST_VLAN);
        when(flowRepository.findFlowIdsByEndpoint(eq(SRC_SWITCH_ID), eq(SRC_PORT))).thenReturn(singletonList(flow));
        when(flowRepository.findFlowIdsByEndpoint(eq(DST_SWITCH_ID), eq(DST_PORT))).thenReturn(singletonList(flow));

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);

        target = new FlowValidator(repositoryFactory);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setFlowId("another_test_flow");
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setDestSwitch(destSwitch);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(0);


        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setFlowId("another_test_flow");
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setDestSwitch(destSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setFlowId("another_test_flow");
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN + 1);
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(DST_PORT);
        flow.setDestVlan(DST_VLAN + 1);
        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN);
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(DST_PORT);
        flow.setDestVlan(0);
        flow.setFlowId("another_test_flow");
        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN + 1);
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(DST_PORT);
        flow.setDestVlan(DST_VLAN);
        flow.setFlowId("another_test_flow");
        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfDestinationVlanIsNotOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setFlowId("another_test_flow");
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN + 1);
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(DST_PORT);
        flow.setDestVlan(DST_VLAN + 1);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailForNegativeBandwidth() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setBandwidth(-1);

        target.checkBandwidth(flow);
    }

    @Test
    public void shouldNotFailOnCheckSwitches() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(destSwitch);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldNotFailOnSingleSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(srcSwitch);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnSourceSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch failSrcSwitch = new Switch();
        failSrcSwitch.setSwitchId(FAIL_SRC_SWITCH_ID);

        Switch destSwitch = new Switch();
        destSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setSrcSwitch(failSrcSwitch);
        flow.setDestSwitch(destSwitch);
        String expectedMessage = String.format("Source switch %s is not connected to the controller",
                FAIL_SRC_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnDestinationSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        Switch failDestSwitch = new Switch();
        failDestSwitch.setSwitchId(FAIL_DST_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(failDestSwitch);
        String expectedMessage =
                String.format("Destination switch %s is not connected to the controller", FAIL_DST_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnSourceAndDestinationSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch failSrcSwitch = new Switch();
        failSrcSwitch.setSwitchId(FAIL_SRC_SWITCH_ID);
        flow.setSrcSwitch(failSrcSwitch);
        Switch failDestSwitch = new Switch();
        failDestSwitch.setSwitchId(FAIL_DST_SWITCH_ID);
        flow.setDestSwitch(failDestSwitch);
        String expectedMessage =
                String.format("Source switch %s and Destination switch %s are not connected to the controller",
                        FAIL_SRC_SWITCH_ID, FAIL_DST_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnOneSwitchFlowWithEqualPortsAndVlans() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(srcSwitch);

        flow.setSrcPort(SRC_PORT);
        flow.setDestPort(SRC_PORT);

        flow.setSrcVlan(SRC_VLAN);
        flow.setDestVlan(SRC_VLAN);

        String expectedMessage = "It is not allowed to create one-switch flow for the same ports and vlans";

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkOneSwitchFlowHasNoConflicts(flow);
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualPortsButDifferentVlans() throws SwitchValidationException {
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(srcSwitch);

        flow.setSrcPort(SRC_PORT);
        flow.setDestPort(SRC_PORT);

        flow.setSrcVlan(SRC_VLAN);
        flow.setDestVlan(DST_VLAN);

        target.checkOneSwitchFlowHasNoConflicts(flow);
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualVlansButDifferentPorts() throws SwitchValidationException {
        Flow flow = new Flow();

        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(srcSwitch);

        flow.setSrcPort(SRC_PORT);
        flow.setDestPort(DST_PORT);

        flow.setSrcVlan(SRC_VLAN);
        flow.setDestVlan(SRC_VLAN);

        target.checkOneSwitchFlowHasNoConflicts(flow);
    }
}
