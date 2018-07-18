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

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class FlowTest {
    @Test
    public void sterilisationRoundTripTest() throws IOException {
        Flow source = new Flow(
                "flowId", 100L, true, 200, "description", "now",
                new SwitchId("ff:00:01"), new SwitchId("ff:00:02"), 1, 2, 30, 40, 0, 50,
                null, null);

        String encoded = MAPPER.writeValueAsString(source);
        Flow decoded = MAPPER.readValue(encoded, Flow.class);

        Assert.assertEquals("Flow object have been mangled in serialisation/deserialization loop", source, decoded);
    }
}
