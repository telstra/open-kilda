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

package org.openkilda.floodlight.service.ping;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.SwitchId;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

public class PingServiceTest extends EasyMockSupport {
    private PingService pingService = new PingService();
    private FloodlightModuleContext moduleContext = new FloodlightModuleContext();
    private PathVerificationService pathVerificationService = new PathVerificationService();

    @Mock
    private IOFSwitch switchAlpha;
    private DatapathId dpIdAlpha = DatapathId.of(0xfffe000000000001L);

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(IOFSwitchService.class, createMock(IOFSwitchService.class));
        moduleContext.addService(InputService.class, createMock(InputService.class));
        moduleContext.addConfigParam(pathVerificationService, "hmac256-secret", "secret");

        expect(switchAlpha.getId()).andReturn(dpIdAlpha).anyTimes();
        expect(switchAlpha.getOFFactory()).andReturn(new OFFactoryVer13()).anyTimes();
        expect(switchAlpha.getLatency()).andReturn(U64.of(8)).anyTimes();
    }

    /**
     * Cookie match.
     */
    @Test
    public void isCookieMismatch0() {
        replayAll();

        OFPacketIn message = switchAlpha.getOFFactory().buildPacketIn()
                .setReason(OFPacketInReason.ACTION).setXid(1)
                .setCookie(U64.of(PingService.OF_CATCH_RULE_COOKIE.getValue()))
                .build();
        FloodlightContext messageMetadata = new FloodlightContext();
        OfInput input = new OfInput(switchAlpha, message, messageMetadata);

        Assert.assertFalse(pingService.isCookieMismatch(input));
    }

    /**
     * Reserved/invalid cookie.
     */
    @Test
    public void isCookieMismatch1() {
        replayAll();

        OFPacketIn message = switchAlpha.getOFFactory().buildPacketIn()
                .setReason(OFPacketInReason.ACTION).setXid(2)
                .setCookie(U64.of(-1))
                .build();
        FloodlightContext messageMetadata = new FloodlightContext();
        OfInput input = new OfInput(switchAlpha, message, messageMetadata);

        Assert.assertFalse(pingService.isCookieMismatch(input));
    }

    /**
     * Cookie is unsupported by protocol.
     */
    @Test
    public void isCookieMismatch2() {
        OFPacketIn message = createMock(OFPacketIn.class);
        expect(message.getType()).andReturn(OFType.PACKET_IN).times(2);
        expect(message.getCookie())
                .andThrow(new UnsupportedOperationException("Forced unsupported operation exception"));
        replayAll();

        FloodlightContext messageMetadata = new FloodlightContext();
        OfInput input = new OfInput(switchAlpha, message, messageMetadata);
        Assert.assertFalse(pingService.isCookieMismatch(input));
    }

    /**
     * Cookie doesn't match.
     */
    @Test
    public void isCookieMismatch3() {
        replayAll();

        OFPacketIn message = switchAlpha.getOFFactory().buildPacketIn()
                .setReason(OFPacketInReason.ACTION).setXid(3)
                .setCookie(U64.of(PingService.OF_CATCH_RULE_COOKIE.getValue() + 1))
                .build();
        FloodlightContext messageMetadata = new FloodlightContext();
        OfInput input = new OfInput(switchAlpha, message, messageMetadata);

        Assert.assertTrue(pingService.isCookieMismatch(input));
    }

    @Test
    public void wrapUnwrapCycle() throws Exception {
        moduleContext.getServiceImpl(InputService.class)
                .addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));

        replayAll();

        pingService.init(moduleContext);

        DatapathId dpIdBeta = DatapathId.of(0xfffe000000000002L);
        Ping ping = new Ping(
                (short) 0x100,
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9));

        byte[] payload = new byte[] {0, 1, 2, 3, 4};
        Ethernet wrapped = pingService.wrapData(ping, payload);
        byte[] unwrapped = pingService.unwrapData(dpIdBeta, wrapped);

        Assert.assertNotNull(unwrapped);
        Assert.assertEquals(payload, unwrapped);
    }
}
