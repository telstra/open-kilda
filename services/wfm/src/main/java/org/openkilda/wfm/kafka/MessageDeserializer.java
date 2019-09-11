/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.kafka;

import org.openkilda.messaging.Message;
import org.openkilda.wfm.topology.utils.SerializationUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class MessageDeserializer extends Deserializer<Message> {

    @Override
    protected Message jsonDecode(byte[] data) throws IOException {
        return SerializationUtils.MAPPER.readValue(data, Message.class);
    }
}
