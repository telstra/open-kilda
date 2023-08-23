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

package org.openkilda.messaging.info.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.PingMeters;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import org.junit.jupiter.api.Test;

public class FlowPingResponseTest {
    StringSerializer serializer = new StringSerializer();

    @Test
    public void serializeLoop() throws Exception {
        NetworkEndpoint endpointAlpha = new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:01"), 8);
        NetworkEndpoint endpointBeta = new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:02"), 10);
        UniFlowPingResponse forward = new UniFlowPingResponse(
                new Ping(endpointAlpha, endpointBeta,
                        new FlowTransitEncapsulation(4, FlowEncapsulationType.TRANSIT_VLAN), 5),
                new PingMeters(1L, 2L, 3L),
                null);
        UniFlowPingResponse reverse = new UniFlowPingResponse(
                new Ping(endpointBeta, endpointAlpha,
                        new FlowTransitEncapsulation(4, FlowEncapsulationType.TRANSIT_VLAN), 5),
                new PingMeters(3L, 2L, 1L),
                null);

        FlowPingResponse origin = new FlowPingResponse("flowId-" + getClass().getSimpleName(), forward, reverse, null);
        InfoMessage wrapper = new InfoMessage(origin, System.currentTimeMillis(), getClass().getSimpleName());

        serializer.serialize(wrapper);
        InfoMessage decodedWrapper = (InfoMessage) serializer.deserialize();
        InfoData decoded = decodedWrapper.getData();

        assertEquals(origin, decoded, String.format("%s object have been mangled in serialisation/deserialization loop",
                origin.getClass().getName()));
    }
}
