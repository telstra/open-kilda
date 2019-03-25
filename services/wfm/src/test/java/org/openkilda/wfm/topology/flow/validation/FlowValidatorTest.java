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
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
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

        IslRepository islRepository = mock(IslRepository.class);
        Isl isl = Isl.builder().srcSwitch(srcSwitch).srcPort(srtPort).destSwitch(dstSwitch).destPort(dstPort).build();
        when(islRepository.findBySrcEndpoint(eq(srcSwitchId), eq(srtPort))).thenReturn(singletonList(isl));
        when(islRepository.findByDestEndpoint(eq(dstSwitchId), eq(dstPort))).thenReturn(singletonList(isl));

        FlowRepository flowRepository = mock(FlowRepository.class);
        Flow flow = new Flow();
        flow.setFlowId(flowId);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(dstSwitch);
        flow.setSrcPort(srtPort);
        flow.setDestPort(dstPort);
        flow.setSrcVlan(srcVlan);
        flow.setDestVlan(dstVlan);
        when(flowRepository.findFlowIdsByEndpoint(eq(srcSwitchId), eq(srtPort))).thenReturn(singletonList(flow));
        when(flowRepository.findFlowIdsByEndpoint(eq(dstSwitchId), eq(dstPort))).thenReturn(singletonList(flow));

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
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        Switch dstSwitch = new Switch();
        dstSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setDestSwitch(dstSwitch);
        flow.setDestPort(DST_PORT + 1);

        target.checkFlowForIslConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationPortIsOccupiedByIsl() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT + 1);
        Switch dstSwitch = new Switch();
        dstSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setDestSwitch(dstSwitch);
        flow.setDestPort(DST_PORT);

        target.checkFlowForIslConflicts(flow);
    }

    @Test
    public void shouldNotFailIfPortIsNotOccupiedByIsl() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT + 1);
        Switch dstSwitch = new Switch();
        dstSwitch.setSwitchId(DST_SWITCH_ID);
        flow.setDestSwitch(dstSwitch);
        flow.setDestPort(DST_PORT + 1);

        target.checkFlowForIslConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, 0, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(SRC_VLAN);
        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupiedAndRequestedSrcVlanIsZero() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 10, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(SRC_PORT);
        flow.setSrcVlan(0);
        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
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
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, 0, FLOW_ID);
        Flow flow = new Flow();
        flow.setFlowId(ANOTHER_FLOW_ID);
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
        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
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
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
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
        defaultSetUp(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, DST_SWITCH_ID, DST_PORT, DST_VLAN, FLOW_ID);
        Flow flow = new Flow();
        flow.setBandwidth(-1);

        target.checkBandwidth(flow);
    }

    @Test
    public void shouldNotFailOnCheckSwitches() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);
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
        setUpWithValidateSwitch(SRC_SWITCH_ID, SRC_SWITCH_ID);
        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(srcSwitch);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnSourceSwitchCheck() throws SwitchValidationException {
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);
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
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);
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
        setUpWithValidateSwitch(SRC_SWITCH_ID, DST_SWITCH_ID);
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
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

        Flow flow = new Flow();
        Switch srcSwitch = new Switch();
        srcSwitch.setSwitchId(SRC_SWITCH_ID);
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(srcSwitch);
        flow.setFlowId(FLOW_ID);
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
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

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
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        target = new FlowValidator(repositoryFactory);

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
