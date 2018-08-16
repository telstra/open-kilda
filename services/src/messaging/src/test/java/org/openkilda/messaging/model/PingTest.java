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

package org.openkilda.messaging.model;

import org.openkilda.messaging.ObjectSerializer;

import org.junit.Assert;
import org.junit.Test;

public class PingTest implements ObjectSerializer {
    @Test
    public void serializeLoop() throws Exception {
        Ping origin = new Ping(
                (short) 0x100,
                new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:01"), 8),
                new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:02"), 10));

        serialize(origin);
        Ping decoded = (Ping) deserialize();

        Assert.assertEquals(
                String.format("%s object have been mangled in serialisation/deserialization loop",
                        origin.getClass().getName()),
                origin, decoded);
    }

    @Test
    public void sourceVlanValues() {
        final NetworkEndpoint source = new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:01"), 8);
        final NetworkEndpoint dest = new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:02"), 10);
        Ping ping;

        ping = new Ping(null, source, dest);
        Assert.assertNull(ping.getSourceVlanId());

        ping = new Ping((short) -1, source, dest);
        Assert.assertNull(ping.getSourceVlanId());

        ping = new Ping((short) 0, source, dest);
        Assert.assertNull(ping.getSourceVlanId());

        ping = new Ping((short) 1, source, dest);
        Assert.assertEquals(1, (short) ping.getSourceVlanId());
    }
}
