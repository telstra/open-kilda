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

package org.openkilda.messaging;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;

import org.junit.Assert;

public abstract class JsonSerializeAbstractTest implements StringSerializer {
    protected void commandSerializeLoop(CommandData origin) throws Exception {
        CommandMessage wrapper = new CommandMessage(origin, 0, "serialize-loop");
        serialize(wrapper);

        CommandMessage decodedWrapper = (CommandMessage) deserialize();
        CommandData decoded = decodedWrapper.getData();
        validate(origin, decoded);
    }

    protected void infoSerializeLoop(InfoData origin) throws Exception {
        InfoMessage wrapper = new InfoMessage(origin, 0, "serialize-loop");
        serialize(wrapper);

        InfoMessage decodedWrapper = (InfoMessage) deserialize();
        InfoData decoded = decodedWrapper.getData();
        validate(origin, decoded);
    }

    protected void validate(MessageData origin, MessageData decoded) {
        Assert.assertEquals(
                String.format("%s object have been mangled in serialisation/deserialization loop",
                        origin.getClass().getName()),
                origin, decoded);
    }
}
