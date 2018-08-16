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

package org.openkilda.messaging.te.request;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.model.LinkProps;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.SwitchId;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class LinkPropsPutTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        LinkPropsPut origin = makeRequest();
        CommandMessage wrapper = new CommandMessage(origin, System.currentTimeMillis(), getClass().getCanonicalName());

        serialize(wrapper);

        CommandMessage result = (CommandMessage) deserialize();
        LinkPropsPut reconstructed = (LinkPropsPut) result.getData();

        Assert.assertEquals(origin, reconstructed);
    }

    /**
     * Produce {@link LinkPropsPut} request with predefined data.
     */
    public static LinkPropsPut makeRequest() {
        HashMap<String, String> keyValuePairs = new HashMap<>();
        keyValuePairs.put("cost", "10000");

        LinkProps linkProps = new LinkProps(
                new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:01"), 8),
                new NetworkEndpoint(new SwitchId("ff:fe:00:00:00:00:00:02"), 8),
                keyValuePairs);
        return new LinkPropsPut(linkProps);
    }
}
