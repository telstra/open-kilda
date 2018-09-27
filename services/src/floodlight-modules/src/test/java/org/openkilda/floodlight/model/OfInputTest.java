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

package org.openkilda.floodlight.model;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;

public class OfInputTest extends EasyMockSupport {
    private static final U64 cookieAlpha = U64.of(1);
    private static final U64 cookieBeta = U64.of(2);

    @Mock(type = MockType.NICE)
    private Logger callerLogger;

    @Mock
    private IOFSwitch ofSwitch;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        expect(ofSwitch.getId()).andReturn(DatapathId.of(0xfffe000000000001L)).anyTimes();
        expect(ofSwitch.getOFFactory()).andReturn(new OFFactoryVer13()).anyTimes();
        expect(ofSwitch.getLatency()).andReturn(U64.of(8)).anyTimes();

        replayAll();
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    /**
     * Cookie match.
     */
    @Test
    public void isCookieMismatch0() {
        OfInput input = makeInput(U64.of(cookieAlpha.getValue()));
        Assert.assertFalse(input.packetInCookieMismatch(cookieAlpha, callerLogger));
    }

    /**
     * Reserved/invalid cookie.
     */
    @Test
    public void isCookieMismatch1() {
        OfInput input = makeInput(U64.of(-1));
        Assert.assertFalse(input.packetInCookieMismatch(cookieAlpha, callerLogger));

        input = makeInput(U64.ZERO);
        Assert.assertFalse(input.packetInCookieMismatch(cookieAlpha, callerLogger));
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
        replay(message);

        FloodlightContext messageMetadata = new FloodlightContext();
        OfInput input = new OfInput(ofSwitch, message, messageMetadata);
        Assert.assertFalse(input.packetInCookieMismatch(cookieAlpha, callerLogger));
    }

    /**
     * Cookie doesn't match.
     */
    @Test
    public void isCookieMismatch3() {
        OfInput input = makeInput(cookieAlpha);
        Assert.assertTrue(input.packetInCookieMismatch(cookieBeta, callerLogger));
    }

    private OfInput makeInput(U64 cookie) {
        OFPacketIn message = ofSwitch.getOFFactory().buildPacketIn()
                .setReason(OFPacketInReason.ACTION).setXid(1)
                .setCookie(cookie)
                .build();
        FloodlightContext messageMetadata = new FloodlightContext();
        return new OfInput(ofSwitch, message, messageMetadata);
    }
}
