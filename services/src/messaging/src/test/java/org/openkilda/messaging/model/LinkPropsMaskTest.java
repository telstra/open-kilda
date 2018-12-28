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

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

public class LinkPropsMaskTest {

    private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testLinkPropsSerialization() throws Exception {
        NetworkEndpointMask source = new NetworkEndpointMask(new SwitchId("ff:fe:00:00:00:00:00:01"), 8);
        NetworkEndpointMask dest = new NetworkEndpointMask(new SwitchId("ff:fe:00:00:00:00:00:02"), null);

        LinkPropsMask origin = new LinkPropsMask(source, dest);
        String json = mapper.writeValueAsString(origin);

        LinkPropsMask reconstructed = mapper.readValue(json, LinkPropsMask.class);
        Assert.assertEquals(origin, reconstructed);
    }
}
