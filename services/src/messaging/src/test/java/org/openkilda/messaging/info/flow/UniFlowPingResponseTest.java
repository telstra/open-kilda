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

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.PingMeters;
import org.openkilda.model.SwitchId;

import org.junit.Assert;
import org.junit.Test;

public class UniFlowPingResponseTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        NetworkEndpoint endpointAlpha = new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:01"), 8);
        NetworkEndpoint endpointBeta = new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:02"), 10);
        UniFlowPingResponse origin = new UniFlowPingResponse(
                new Ping((short) 0x200, endpointBeta, endpointAlpha),
                new PingMeters(3L, 2L, 1L),
                null);
        InfoMessage wrapper = new InfoMessage(origin, System.currentTimeMillis(), getClass().getSimpleName());

        serialize(wrapper);
        InfoMessage decodedWrapper = (InfoMessage) deserialize();
        InfoData decoded = decodedWrapper.getData();

        Assert.assertEquals(
                String.format("%s object have been mangled in serialisation/deserialization loop",
                        origin.getClass().getName()),
                origin, decoded);
    }
}
