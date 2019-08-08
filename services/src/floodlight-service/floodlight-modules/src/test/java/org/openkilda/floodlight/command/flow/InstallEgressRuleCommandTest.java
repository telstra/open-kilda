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

package org.openkilda.floodlight.command.flow;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.test.standard.OutputCommands.ofFactory;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.SwitchFeature.BFD;
import static org.openkilda.model.SwitchFeature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;

import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.test.standard.OutputCommands;
import org.openkilda.floodlight.test.standard.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.IOFSwitch;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Set;
import java.util.UUID;

public class InstallEgressRuleCommandTest {
    private static final String FLOW_ID = "test_flow";
    private static final SwitchId SWITCH_ID = new SwitchId(1);
    private IOFSwitch iofSwitch;
    private FeatureDetectorService featureDetectorService;
    private static final OutputCommands scheme = new ReplaceSchemeOutputCommands();

    @Before
    public void setUp() {
        iofSwitch = createMock(IOFSwitch.class);
        featureDetectorService = createMock(FeatureDetectorService.class);
        Set<SwitchFeature> features = Sets.newHashSet(METERS, BFD, GROUP_PACKET_OUT_CONTROLLER,
                NOVIFLOW_COPY_FIELD, RESET_COUNTS_FLAG);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(features);

        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        replay(iofSwitch);
        replay(featureDetectorService);
    }

    @Test
    public void testGetCommandsVlanReplace() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.REPLACE,
                outVlan,
                transitEncapsulationId,
                TRANSIT_VLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressReplaceFlowMod(inPort, outPort, transitEncapsulationId, outVlan, cookie,
                TRANSIT_VLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVlanPop() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.POP,
                outVlan,
                transitEncapsulationId,
                TRANSIT_VLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressPopFlowMod(inPort, outPort, transitEncapsulationId, cookie,
                TRANSIT_VLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVlanPush() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.PUSH,
                outVlan,
                transitEncapsulationId,
                TRANSIT_VLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressPushFlowMod(inPort, outPort, transitEncapsulationId, outVlan, cookie,
                TRANSIT_VLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVlanNone() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.NONE,
                outVlan,
                transitEncapsulationId,
                TRANSIT_VLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressNoneFlowMod(inPort, outPort, transitEncapsulationId, cookie,
                TRANSIT_VLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }


    @Test
    public void testGetCommandsVxlanReplace() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.REPLACE,
                outVlan,
                transitEncapsulationId,
                VXLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressReplaceFlowMod(inPort, outPort, transitEncapsulationId, outVlan, cookie,
                VXLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVxlanPop() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.POP,
                outVlan,
                transitEncapsulationId,
                VXLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressPopFlowMod(inPort, outPort, transitEncapsulationId, cookie,
                VXLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVxlanPush() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.PUSH,
                outVlan,
                transitEncapsulationId,
                VXLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressPushFlowMod(inPort, outPort, transitEncapsulationId, outVlan, cookie,
                VXLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVxlanNone() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallEgressRuleCommand command = new InstallEgressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                OutputVlanType.NONE,
                outVlan,
                transitEncapsulationId,
                VXLAN,
                SWITCH_ID);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.egressNoneFlowMod(inPort, outPort, transitEncapsulationId, cookie,
                VXLAN, DatapathId.of(SWITCH_ID.toLong())), result);
    }

}
