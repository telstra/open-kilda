/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.pathverification;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.openkilda.floodlight.model.OfInput;
import org.openkilda.messaging.Message;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.HashMap;

@RunWith(EasyMockRunner.class)
public class PathVerificationPacketSignTest extends PathVerificationPacketInTest {

    private OFPacketIn ofPacketIn;
    private FloodlightContext context;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        final OFPacketOut packetOut = pvs.generateDiscoveryPacket(sw1, OFPort.of(1), true, null);

        ofPacketIn = EasyMock.createMock(OFPacketIn.class);

        context = new FloodlightContext();

        expect(ofPacketIn.getType()).andReturn(OFType.PACKET_IN).anyTimes();
        expect(ofPacketIn.getXid()).andReturn(0L).anyTimes();
        expect(ofPacketIn.getVersion()).andReturn(packetOut.getVersion()).anyTimes();
        expect(ofPacketIn.getCookie()).andReturn(PathVerificationService.OF_CATCH_RULE_COOKIE);

        Match match = EasyMock.createMock(Match.class);
        expect(match.get(MatchField.IN_PORT)).andReturn(OFPort.of(1)).anyTimes();
        replay(match);
        expect(ofPacketIn.getMatch()).andReturn(match).anyTimes();
        replay(ofPacketIn);

        IPacket expected = new Ethernet().deserialize(packetOut.getData(), 0,
                packetOut.getData().length);

        context.getStorage().put(IFloodlightProviderService.CONTEXT_PI_PAYLOAD, expected);

        HashMap<DatapathId, IOFSwitch> switches = new HashMap<>();
        switches.put(sw1.getId(), sw1);
        switches.put(sw2.getId(), sw2);
        mockSwitchManager.setSwitches(switches);
    }

    @Test
    public void testSignPacketPositive() throws Exception {
        producerService.sendMessageAndTrackWithZk(anyObject(), anyObject(), anyObject(Message.class));
        expectLastCall().times(2);
        replay(producerService);

        pvs.handlePacketIn(new OfInput(sw2, ofPacketIn, context));
        verify(producerService);
    }

    @Test
    public void testInputSwitchNotFound() {
        producerService.sendMessageAndTrack(anyObject(), anyObject(), anyObject(Message.class));
        expectLastCall().andAnswer((IAnswer) () -> {
            Assert.fail();
            return null;
        }).anyTimes();
        replay(producerService);

        IOFSwitch sw = buildMockIoFSwitch(13L, null, factory, swDescription, dstIpTarget);
        replay(sw);

        pvs.handlePacketIn(new OfInput(sw, ofPacketIn, context));
    }

    @Test
    public void testSignPacketMissedSign() {
        replay(producerService);

        OFPacketOut noSignPacket = pvs.generateDiscoveryPacket(sw1, OFPort.of(1), false, null);
        IPacket noSignPacketData = new Ethernet().deserialize(noSignPacket.getData(), 0,
                noSignPacket.getData().length);
        context.getStorage().put(IFloodlightProviderService.CONTEXT_PI_PAYLOAD, noSignPacketData);

        pvs.handlePacketIn(new OfInput(sw2, ofPacketIn, context));

        verify(producerService);
    }

    @Test
    public void testSignPacketInvalidSign() throws FloodlightModuleException {
        replay(producerService);

        pvs.initAlgorithm("secret2");
        pvs.handlePacketIn(new OfInput(sw2, ofPacketIn, context));

        verify(producerService);
    }
}
