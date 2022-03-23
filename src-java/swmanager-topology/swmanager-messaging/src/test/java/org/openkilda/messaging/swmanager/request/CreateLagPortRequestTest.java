/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.swmanager.request;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

public class CreateLagPortRequestTest {
    StringSerializer serializer = new StringSerializer();

    @Test
    public void serializeLoop() throws Exception {
        CreateLagPortRequest origin = makeRequest();
        CommandMessage message = new CommandMessage(origin, System.currentTimeMillis(), getClass().getCanonicalName());

        serializer.serialize(message);
        CommandMessage reconstructedMessage = (CommandMessage) serializer.deserialize();
        CreateLagPortRequest reconstructed = (CreateLagPortRequest) reconstructedMessage.getData();

        Assert.assertEquals(origin, reconstructed);
    }

    public static CreateLagPortRequest makeRequest() {
        return new CreateLagPortRequest(new SwitchId(1), Sets.newHashSet(1, 2, 3));
    }
}
