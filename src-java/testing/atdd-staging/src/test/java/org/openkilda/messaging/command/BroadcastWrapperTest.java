/* Copyright 2020 Telstra Open Source
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

package org.openkilda.messaging.command;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Test;

public class BroadcastWrapperTest {
    @Test
    public void serializeLoop() throws Exception {
        StatsRequest payload = new StatsRequest(
                ImmutableList.of(new SwitchId(1), new SwitchId(2)));
        BroadcastWrapper wrapper = new BroadcastWrapper(ImmutableSet.of(new SwitchId(2), new SwitchId(3)), payload);
        CommandMessage envelope = new CommandMessage(wrapper, System.currentTimeMillis(), getClass().getSimpleName());

        String json = Utils.MAPPER.writeValueAsString(envelope);
        verifyDecoding(Utils.MAPPER.readValue(json, CommandMessage.class), wrapper);
    }

    private void verifyDecoding(CommandMessage envelope, CommandData expected) {
        CommandData decoded = envelope.getData();

        Assert.assertEquals(
                String.format("%s object have been mangled in serialisation/deserialization loop",
                        expected.getClass().getName()),
                expected, decoded);
    }
}
