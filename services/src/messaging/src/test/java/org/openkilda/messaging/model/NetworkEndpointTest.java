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

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.Utils;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class NetworkEndpointTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        NetworkEndpoint origin = new NetworkEndpoint("ff:fe:00:00:00:00:00:01", 8);

        serialize(origin);

        NetworkEndpoint reconstructed = (NetworkEndpoint) deserialize();
        Assert.assertEquals(origin, reconstructed);
    }

    @Override
    public Object deserialize() throws IOException {
        return Utils.MAPPER.readValue(strings.poll(), NetworkEndpoint.class);
    }

    @Override
    public void serialize(Object subject) throws IOException {
        strings.add(Utils.MAPPER.writeValueAsString(subject));
    }
}
