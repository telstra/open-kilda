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

package org.openkilda.floodlight.pathverification;

import static org.junit.Assert.assertEquals;

import net.floodlightcontroller.packet.LLDPTLV;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SwitchTimestampTlvTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testTimestampTlv() {
        byte type = 0x04;
        LLDPTLV timestampTlv = PathVerificationService.switchTimestampTlv(type);

        assertEquals(12, timestampTlv.getLength());
        assertEquals(type, timestampTlv.getValue()[3]);

        for (int i = 4; i < timestampTlv.getLength(); i++) {
            assertEquals(0, timestampTlv.getValue()[i]);
        }
    }
}
