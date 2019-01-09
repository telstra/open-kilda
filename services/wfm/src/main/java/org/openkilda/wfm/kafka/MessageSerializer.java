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

package org.openkilda.wfm.kafka;

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class MessageSerializer implements Serializer<Message> {

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No-op
    }

    @Override
    public byte[] serialize(String topic, Message data) {
        try {
            byte[] result = null;
            if (data != null) {
                result = MAPPER.writeValueAsBytes(data);
            }
            return result;
        } catch (IOException e) {
            log.error(format("Failed to serialize message: %s, for topic: %s", data, topic), e);
            throw new SerializationException(e.getMessage());
        }
    }

    @Override
    public void close() {
        // No-op
    }
}
