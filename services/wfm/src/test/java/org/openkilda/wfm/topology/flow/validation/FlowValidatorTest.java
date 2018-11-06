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

import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.provider.PathComputerAuth;
import org.openkilda.wfm.topology.flow.MockedPathComputerAuth;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FlowValidatorTest {
    private FlowValidator target = new FlowValidator(flowCache, pathComputerAuth.getPathComputer());

    private static final FlowCache flowCache = new FlowCache();
    private static final PathComputerAuth pathComputerAuth = new MockedPathComputerAuth();
    private static final SwitchId SRC_SWITCH = new SwitchId("00:00:00:00:00:00:00:01");
    private static final int SRC_PORT = 1;
    private static final int SRC_VLAN = 1;
    private static final SwitchId DST_SWITCH = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int DST_PORT = 5;
    private static final int DST_VLAN = 5;
    private static final SwitchId FAIL_SRC_SWITCH = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId FAIL_DST_SWITCH = new SwitchId("00:00:00:00:00:00:00:04");

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void initCache() {
        Flow flow = new Flow();
        flow.setFlowId("1");
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);

        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN);
        flowCache.pushFlow(new FlowPair<>(flow, flow));
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(0);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(0);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfDestinationVlanIsNotOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN + 1);

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
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setDestinationSwitch(DST_SWITCH);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldNotFailOnSingleSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setDestinationSwitch(SRC_SWITCH);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnSourceSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(FAIL_SRC_SWITCH);
        flow.setDestinationSwitch(DST_SWITCH);
        String expectedMessage = String.format("Source switch %s is not connected to the controller", FAIL_SRC_SWITCH);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnDestinationSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setDestinationSwitch(FAIL_DST_SWITCH);
        String expectedMessage =
                String.format("Destination switch %s is not connected to the controller", FAIL_DST_SWITCH);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnSourceAndDestinationSwitchCheck() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(FAIL_SRC_SWITCH);
        flow.setDestinationSwitch(FAIL_DST_SWITCH);
        String expectedMessage =
                String.format("Source switch %s and Destination switch %s are not connected to the controller",
                FAIL_SRC_SWITCH, FAIL_DST_SWITCH);

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkSwitchesExists(flow);
    }

    @Test
    public void shouldFailOnOneSwitchFlowWithEqualPortsAndVlans() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setDestinationSwitch(SRC_SWITCH);

        flow.setSourcePort(SRC_PORT);
        flow.setDestinationPort(SRC_PORT);

        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationVlan(SRC_VLAN);

        String expectedMessage = "It is not allowed to create one-switch flow for the same ports and vlans";

        thrown.expect(SwitchValidationException.class);
        thrown.expectMessage(expectedMessage);

        target.checkOneSwitchFlowHasNoConflicts(flow);
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualPortsButDifferentVlans() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setDestinationSwitch(SRC_SWITCH);

        flow.setSourcePort(SRC_PORT);
        flow.setDestinationPort(SRC_PORT);

        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationVlan(DST_VLAN);

        target.checkOneSwitchFlowHasNoConflicts(flow);
    }

    @Test
    public void shouldNotFailOnOneSwitchFlowWithEqualVlansButDifferentPorts() throws SwitchValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setDestinationSwitch(SRC_SWITCH);

        flow.setSourcePort(SRC_PORT);
        flow.setDestinationPort(DST_PORT);

        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationVlan(SRC_VLAN);

        target.checkOneSwitchFlowHasNoConflicts(flow);
    }
}
