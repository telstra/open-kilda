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

import org.openkilda.messaging.Utils;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class FlowVerificationRequestTest {
    @Test
    public void sterilisationRoundTripTest() throws IOException {
        FlowVerificationRequest source = new FlowVerificationRequest("flowId", 5000);

        String encoded = Utils.MAPPER.writeValueAsString(source);
        FlowVerificationRequest decoded = Utils.MAPPER.readValue(encoded, FlowVerificationRequest.class);

        Assert.assertEquals(
                String.format("%s object have been mangled in serialisation/deserialization loop",
                        source.getClass().getName()),
                source, decoded);
    }
}
