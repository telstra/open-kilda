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

import org.openkilda.messaging.info.event.PortInfoData;

import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.HashMap;
import java.util.Map;

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

    @Test
    public void testPortChangeTypeMapping() {
        OFPortDesc portDesc = OFFactoryVer13.INSTANCE.buildPortDesc()
                .setPortNo(OFPort.of(1))
                .setName("test")
                .build();

        Map<org.openkilda.messaging.info.event.PortChangeType, net.floodlightcontroller.core.PortChangeType> expected
                = new HashMap<>();
        expected.put(org.openkilda.messaging.info.event.PortChangeType.ADD,
                     net.floodlightcontroller.core.PortChangeType.ADD);
        expected.put(org.openkilda.messaging.info.event.PortChangeType.OTHER_UPDATE,
                     net.floodlightcontroller.core.PortChangeType.OTHER_UPDATE);
        expected.put(org.openkilda.messaging.info.event.PortChangeType.DELETE,
                     net.floodlightcontroller.core.PortChangeType.DELETE);
        expected.put(org.openkilda.messaging.info.event.PortChangeType.UP,
                     net.floodlightcontroller.core.PortChangeType.UP);
        expected.put(org.openkilda.messaging.info.event.PortChangeType.DOWN,
                     net.floodlightcontroller.core.PortChangeType.DOWN);

        DatapathId dpId = DatapathId.of(1);
        for (Map.Entry<org.openkilda.messaging.info.event.PortChangeType,
                net.floodlightcontroller.core.PortChangeType> entry : expected.entrySet()) {
            PortInfoData encoded = OfPortDescConverter.INSTANCE.toPortInfoData(dpId, portDesc, entry.getValue());
            Assert.assertSame(entry.getKey(), encoded.getState());
        }
    }
}
