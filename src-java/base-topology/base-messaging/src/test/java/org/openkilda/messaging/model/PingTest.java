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
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class PingTest {
    ObjectSerializer serializer;

    public PingTest() throws IOException {
        serializer = new ObjectSerializer();
    }

    @Test
    public void serializeLoop() throws Exception {
        Ping origin = new Ping(new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:01"), 8),
                new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:02"), 10),
                new FlowTransitEncapsulation(2, FlowEncapsulationType.TRANSIT_VLAN), 3);

        serializer.serialize(origin);
        Ping decoded = (Ping) serializer.deserialize();

        Assertions.assertEquals(origin, decoded, String.format("%s object have been mangled in"
                + " serialisation/deserialization loop", origin.getClass().getName()));
    }
}
