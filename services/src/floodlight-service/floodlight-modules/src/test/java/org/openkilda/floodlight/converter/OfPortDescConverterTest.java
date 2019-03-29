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

package org.openkilda.floodlight.converter;

import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.types.OFPort;

public class OfPortDescConverterTest {
    @Test
    public void testReservedPortCheck() {
        for (OFPort port : new OFPort[]{
                OFPort.LOCAL,
                OFPort.ALL,
                OFPort.CONTROLLER,
                OFPort.ANY,
                OFPort.FLOOD,
                OFPort.NO_MASK,
                OFPort.IN_PORT,
                OFPort.NORMAL,
                OFPort.TABLE}) {
            Assert.assertTrue(String.format("Port %s must be detected as RESERVED, but it's not", port),
                              OfPortDescConverter.INSTANCE.isReservedPort(port));
        }

        for (OFPort port : new OFPort[]{
                OFPort.of(1),
                OFPort.of(OFPort.MAX.getPortNumber() - 1)}) {
            Assert.assertFalse(String.format("Port %s must be detected as NOT RESERVED, but it's not", port),
                              OfPortDescConverter.INSTANCE.isReservedPort(port));
        }
    }
}
