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

import org.junit.jupiter.api.Assertions;

public abstract class JsonSerializeAbstractTest {
    StringSerializer serializer = new StringSerializer();

    protected void commandSerializeLoop(CommandData origin) throws Exception {
        CommandMessage wrapper = new CommandMessage(origin, 0, "serialize-loop");
        serializer.serialize(wrapper);

        CommandMessage decodedWrapper = (CommandMessage) serializer.deserialize();
        CommandData decoded = decodedWrapper.getData();
        validate(origin, decoded);
    }

    protected void infoSerializeLoop(InfoData origin) throws Exception {
        InfoMessage wrapper = new InfoMessage(origin, 0, "serialize-loop");
        serializer.serialize(wrapper);

        InfoMessage decodedWrapper = (InfoMessage) serializer.deserialize();
        InfoData decoded = decodedWrapper.getData();
        validate(origin, decoded);
    }

    protected void validate(MessageData origin, MessageData decoded) {
        Assertions.assertEquals(origin, decoded, String.format("%s object have been mangled in "
                + "serialisation/deserialization loop", origin.getClass().getName()));
    }
}
