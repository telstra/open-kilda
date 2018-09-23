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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.openkilda.floodlight.model.OfInput;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TimestampTests {
    private byte[] t1;
    private byte[] t2;
    OfInput input;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        t1 = new byte[]{
                0x5b,
                (byte) 0x8c,
                (byte) 0xf5,
                (byte) 0xad,
                0x14,
                (byte) 0xe0,
                (byte) 0xf8,
                0x3d,
        };

        t2 = new byte[]{
                0x5b,
                (byte) 0x8c,
                (byte) 0xf5,
                (byte) 0xad,
                0x1c,
                0x08,
                0x06,
                0x3d,
        };

        input = EasyMock.createMock(OfInput.class);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testBytearrayToLong() throws Exception {
        assertEquals(1535964589L,
                PathVerificationService.byteArrayToLong(t2, 0, 4));
    }

    @Test
    public void testNoviflowTimstampToLong() throws Exception {
        assertEquals(1535964589350287933L, PathVerificationService.noviflowTimestamp(t1));
    }

    @Test
    public void testLatency() throws Exception {
        long latency = 50;
        long now = System.currentTimeMillis();
        long sendTime = now - latency;

        expect(input.getReceiveTime()).andStubReturn(now);
        replay(input);

        // packet has software timestamp for tx and rx
        double delta = PathVerificationService.calcLatency(
                input,
                sendTime,
                PathVerificationService.noviflowTimestamp(t1),
                PathVerificationService.noviflowTimestamp(t2)
        );
        assertEquals(120000000L, delta, 0);

        // packet has software timestamp for tx only
        delta = PathVerificationService.calcLatency(
                input,
                sendTime,
                PathVerificationService.noviflowTimestamp(t1),
                0
        );
        assertEquals(input.getReceiveTime() * 1000000 - PathVerificationService.noviflowTimestamp(t1),
                delta, 0);

        //packet has software timestamp for rx only
        delta = PathVerificationService.calcLatency(
                input,
                sendTime,
                0,
                PathVerificationService.noviflowTimestamp(t2)
        );
        assertEquals(PathVerificationService.noviflowTimestamp(t2) - sendTime * 1000000,
                delta, 0);

        // packet has no software timestamps
        delta = PathVerificationService.calcLatency(
                input,
                sendTime,
                0,
                0
        );
        assertEquals(latency * 1000000, delta, 0);  // adjusted to nanoseconds
    }
}
