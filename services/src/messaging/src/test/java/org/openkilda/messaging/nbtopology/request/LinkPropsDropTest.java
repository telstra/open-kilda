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

package org.openkilda.messaging.nbtopology.request;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.model.LinkPropsMask;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.model.SwitchId;

import org.junit.Assert;
import org.junit.Test;

public class LinkPropsDropTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        LinkPropsDrop origin = makeRequest();
        CommandMessage wrapper = new CommandMessage(origin, System.currentTimeMillis(), getClass().getCanonicalName());

        serialize(wrapper);
        CommandMessage reconstructedWrapper = (CommandMessage) deserialize();
        LinkPropsDrop reconstructed = (LinkPropsDrop) reconstructedWrapper.getData();

        Assert.assertEquals(origin, reconstructed);
    }

    /**
     * Produce {@link LinkPropsDrop} request with predefined data.
     */
    public static LinkPropsDrop makeRequest() {
        LinkPropsMask mask = new LinkPropsMask(
                new NetworkEndpointMask(new SwitchId("ff:fe:00:00:00:00:00:01"), 8),
                new NetworkEndpointMask(null, null));
        return new LinkPropsDrop(mask);
    }
}
