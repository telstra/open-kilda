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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;

import org.junit.Assert;
import org.junit.Test;

public class FlowPingRequestTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        FlowPingRequest origin = new FlowPingRequest("flowId", 5000);
        CommandMessage wrapper = new CommandMessage(origin, System.currentTimeMillis(), getClass().getSimpleName());

        serialize(wrapper);
        CommandMessage decodedWrapper = (CommandMessage) deserialize();
        CommandData decoded = decodedWrapper.getData();

        Assert.assertEquals(
                String.format("%s object have been mangled in serialisation/deserialization loop",
                        origin.getClass().getName()),
                origin, decoded);
    }
}
