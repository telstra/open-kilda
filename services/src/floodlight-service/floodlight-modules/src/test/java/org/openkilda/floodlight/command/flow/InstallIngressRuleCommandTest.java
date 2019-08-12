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
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.BFD;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.METERS;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.messaging.model.SpeakerSwitchView.Feature.RESET_COUNTS_FLAG;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;

import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.test.standard.OutputCommands;
import org.openkilda.floodlight.test.standard.ReplaceSchemeOutputCommands;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.IOFSwitch;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Set;
import java.util.UUID;

public class InstallIngressRuleCommandTest {

    private static final String FLOW_ID = "test_flow";
    private static final SwitchId SWITCH_ID = new SwitchId(1);
    private static final SwitchId EGRESS_SWITCH_ID = new SwitchId(2);
    private IOFSwitch iofSwitch;
    private FeatureDetectorService featureDetectorService;
    private static final OutputCommands scheme = new ReplaceSchemeOutputCommands();

    @Before
    public void setUp() {
        iofSwitch = createMock(IOFSwitch.class);
        featureDetectorService = createMock(FeatureDetectorService.class);
        Set<Feature> features = Sets.newHashSet(METERS, BFD, GROUP_PACKET_OUT_CONTROLLER,
                    NOVIFLOW_COPY_FIELD, RESET_COUNTS_FLAG);
        expect(featureDetectorService.detectSwitch(iofSwitch)).andStubReturn(features);

        Capture<OFFlowMod> capture = EasyMock.newCapture();
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        replay(iofSwitch);
        replay(featureDetectorService);
    }

    @Test
    public void testGetCommandsVlanReplace() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int inVlan = 12;
        int outTunnelId = 14;
        int meterId = 12;
        int cookie = 22;
        InstallIngressRuleCommand command = new InstallIngressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                10L,
                inVlan,
                OutputVlanType.REPLACE,
                new MeterId(meterId),
                outTunnelId,
                TRANSIT_VLAN,
                EGRESS_SWITCH_ID,
                false);

        OFFlowMod result = command.getInstallRuleCommand(iofSwitch, featureDetectorService);
        assertEquals(scheme.ingressReplaceFlowMod(DatapathId.of(SWITCH_ID.toLong()), inPort, outPort, inVlan,
                outTunnelId, meterId, cookie, TRANSIT_VLAN, DatapathId.of(EGRESS_SWITCH_ID.toLong())), result);
    }

    @Test
    public void testGetCommandsVlanReplaceVxlan() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int inVlan = 12;
        int outTunnelId = 14;
        int meterId = 12;
        int cookie = 22;
        InstallIngressRuleCommand command = new InstallIngressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                10L,
                inVlan,
                OutputVlanType.REPLACE,
                new MeterId(meterId),
                outTunnelId,
                VXLAN,
                EGRESS_SWITCH_ID,
                false);

        OFFlowMod result = command.getInstallRuleCommand(iofSwitch, featureDetectorService);
        assertEquals(scheme.ingressReplaceFlowMod(DatapathId.of(SWITCH_ID.toLong()), inPort, outPort, inVlan,
                outTunnelId, meterId, cookie, VXLAN, DatapathId.of(EGRESS_SWITCH_ID.toLong())), result);
    }

    @Test
    @Ignore
    public void testGetCommandsNoMatchVlan() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int inVlan = 0;
        int outTunnelId = 14;
        int meterId = 12;
        int cookie = 22;
        InstallIngressRuleCommand command = new InstallIngressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                10L,
                inVlan,
                OutputVlanType.PUSH,
                new MeterId(meterId),
                outTunnelId,
                TRANSIT_VLAN,
                EGRESS_SWITCH_ID,
                false);

        OFFlowMod result = command.getInstallRuleCommand(iofSwitch, featureDetectorService);
        assertEquals(scheme.ingressNoMatchVlanIdFlowMod(DatapathId.of(SWITCH_ID.toLong()),
                inPort, outPort, outTunnelId, meterId, cookie,
                TRANSIT_VLAN, DatapathId.of(EGRESS_SWITCH_ID.toLong())), result);
    }

    @Test
    @Ignore
    public void testGetCommandsNoMatchVlanVxlan() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int inVlan = 0;
        int outTunnelId = 14;
        int meterId = 12;
        int cookie = 22;
        InstallIngressRuleCommand command = new InstallIngressRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                10L,
                inVlan,
                OutputVlanType.PUSH,
                new MeterId(meterId),
                outTunnelId,
                VXLAN,
                EGRESS_SWITCH_ID,
                false);

        OFFlowMod result = command.getInstallRuleCommand(iofSwitch, featureDetectorService);
        assertEquals(scheme.ingressNoMatchVlanIdFlowMod(DatapathId.of(SWITCH_ID.toLong()), inPort, outPort, outTunnelId,
                meterId, cookie, VXLAN, DatapathId.of(EGRESS_SWITCH_ID.toLong())), result);
    }
}
