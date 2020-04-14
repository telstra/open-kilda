/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.test.standard;

import static com.google.common.primitives.Bytes.concat;

import net.floodlightcontroller.packet.Ethernet;
import org.projectfloodlight.openflow.types.EthType;

public class PacketTestBase {
    protected Ethernet buildEthernet(byte[]... arrays) {
        byte[] data = concat(arrays);
        Ethernet ethernet = new Ethernet();
        ethernet.deserialize(data, 0, data.length);
        return ethernet;
    }

    protected byte[] ethTypeToByteArray(EthType ethType) {
        return shortToByteArray((short) ethType.getValue());
    }

    protected byte[] shortToByteArray(short s) {
        return new byte[]{(byte) (s >> 8), (byte) s};
    }
}
