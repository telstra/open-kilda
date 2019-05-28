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

package org.openkilda.floodlight.pathverification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimestampTests {
    private byte[] timestampT0 = new byte[] {
            0x5b, (byte) 0x8c, (byte) 0xf5, (byte) 0xad,
            0x14, (byte) 0xe0, (byte) 0xf8, 0x3d};

    @Test
    public void testNoviflowTimstampToLong() {
        assertEquals(1535964589350287933L, PathVerificationService.noviflowTimestamp(timestampT0));
    }
}
