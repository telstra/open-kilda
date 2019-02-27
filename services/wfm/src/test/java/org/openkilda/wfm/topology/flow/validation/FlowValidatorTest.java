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

import org.openkilda.model.FlowPair;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

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

        FlowPair flowPair = new FlowPair(flowId, srcSwitch, srtPort, srcVlan, dstSwitch, dstPort, dstVlan);

        FlowPairRepository flowPairRepository = mock(FlowPairRepository.class);
        when(flowPairRepository.findByEndpoint(eq(srcSwitchId), eq(srtPort)))
                .thenReturn(singletonList(flowPair));
        when(flowPairRepository.findByEndpoint(eq(dstSwitchId), eq(dstPort)))
                .thenReturn(singletonList(flowPair));

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createFlowPairRepository()).thenReturn(flowPairRepository);

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
    public void shouldFailIfSourceVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN, dstSwitch, 0, 0);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN, dstSwitch, 0, 0);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupiedAndRequestedSrcVlanIsZero() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 10, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(new SwitchId("de:ad:be:af:de:ad:be:af")).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, 0, dstSwitch, 0, 0);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN + 1,
                dstSwitch, DST_PORT, DST_VLAN + 1);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN + 1,
                dstSwitch, DST_PORT, DST_VLAN);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN + 1,
                dstSwitch, DST_PORT, DST_VLAN);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test
    public void shouldNotFailIfDestinationVlanIsNotOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN + 1,
                dstSwitch, DST_PORT, DST_VLAN + 1);
        target.checkFlowForEndpointConflicts(flowPair.getForward());
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailForNegativeBandwidth() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, 0, 0, dstSwitch, 0, 0);
        flowPair.getForward().setBandwidth(-1);
        target.checkBandwidth(flowPair.getForward());
    }

    @Test
    public void shouldNotFailOnCheckSwitches() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, 0, 0, dstSwitch, 0, 0);
        target.checkSwitchesExists(flowPair.getForward());
    }

    @Test
    public void shouldNotFailOnSingleSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, SRC_SWITCH_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, 0, 0, srcSwitch, 0, 0);
        target.checkSwitchesExists(flowPair.getForward());
    }

    @Test
    public void shouldFailOnSourceSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch failSrcSwitch = Switch.builder().switchId(FAIL_SRC_SWITCH_ID).build();
        Switch dstSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, failSrcSwitch, 0, 0, dstSwitch, 0, 0);

        String expectedMessage = String.format("Source switch %s is not connected to the controller",
                FAIL_SRC_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);
        target.checkSwitchesExists(flowPair.getForward());
    }

    @Test
    public void shouldFailOnDestinationSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch failDestSwitch = Switch.builder().switchId(FAIL_DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, srcSwitch, 0, 0, failDestSwitch, 0, 0);

        String expectedMessage =
                String.format("Destination switch %s is not connected to the controller", FAIL_DST_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flowPair.getForward());
    }

    @Test
    public void shouldFailOnSourceAndDestinationSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);

        Switch failSrcSwitch = Switch.builder().switchId(FAIL_SRC_SWITCH_ID).build();
        Switch failDestSwitch = Switch.builder().switchId(FAIL_DST_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(ANOTHER_FLOW_ID, failSrcSwitch, 0, 0, failDestSwitch, 0, 0);

        String expectedMessage =
                String.format("Source switch %s and Destination switch %s are not connected to the controller",
                        FAIL_SRC_SWITCH_ID, FAIL_DST_SWITCH_ID);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flowPair.getForward());
    }

    @Test
    public void shouldFailOnOneSwitchFlowWithEqualPortsAndVlans() throws SwitchValidationException {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN, srcSwitch, SRC_PORT, SRC_VLAN);

        String expectedMessage = "It is not allowed to create one-switch flow for the same ports and vlans";

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkOneSwitchFlowHasNoConflicts(flowPair.getForward());
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualPortsButDifferentVlans() throws SwitchValidationException {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(FLOW_ID, srcSwitch, SRC_PORT, SRC_VLAN, srcSwitch, SRC_PORT, DST_VLAN);

        target.checkOneSwitchFlowHasNoConflicts(flowPair.getForward());
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualVlansButDifferentPorts() throws SwitchValidationException {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();

        FlowPair flowPair = new FlowPair(FLOW_ID, srcSwitch, SRC_PORT, DST_PORT, srcSwitch, SRC_PORT, SRC_VLAN);

        target.checkOneSwitchFlowHasNoConflicts(flowPair.getForward());
    }
}
