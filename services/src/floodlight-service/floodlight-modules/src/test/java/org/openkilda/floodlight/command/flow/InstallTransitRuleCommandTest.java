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
import org.openkilda.model.SwitchId;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.IOFSwitch;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.Set;
import java.util.UUID;

public class InstallTransitRuleCommandTest {
    private static final String FLOW_ID = "test_flow";
    private static final SwitchId SWITCH_ID = new SwitchId(1);
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

        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        replay(iofSwitch);
        replay(featureDetectorService);
    }

    @Test
    public void testGetCommandsVlan() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallTransitRuleCommand command = new InstallTransitRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                transitEncapsulationId,
                TRANSIT_VLAN,
                false);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.transitFlowMod(inPort, outPort, transitEncapsulationId, cookie,
                TRANSIT_VLAN), result);
    }

    @Test
    public void testGetCommandsVxlan() throws SwitchOperationException {
        int inPort = 10;
        int outPort = 12;
        int outVlan = 12;
        int transitEncapsulationId = 14;
        int cookie = 22;
        InstallTransitRuleCommand command = new InstallTransitRuleCommand(UUID.randomUUID(),
                FLOW_ID,
                new MessageContext(),
                new Cookie(cookie),
                SWITCH_ID,
                inPort,
                outPort,
                transitEncapsulationId,
                VXLAN,
                false);

        OFMessage result = command.getCommands(iofSwitch, null).get(0).getOfMessage();
        assertEquals(scheme.transitFlowMod(inPort, outPort, transitEncapsulationId, cookie,
                VXLAN), result);
    }
}
