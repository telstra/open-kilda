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
import org.openkilda.messaging.error.DeserializationErrorMessage;
import org.openkilda.wfm.topology.utils.SerializationUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

@Slf4j
public class MessageDeserializer implements Deserializer<Message> {

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No-op
    }

    @Override
    public Message deserialize(String topic, byte[] data) {
        Class<Message> base = Message.class;
        try {
            return SerializationUtils.MAPPER.readValue(data, base);
        } catch (IOException e) {
            String dataAsSting = StringUtils.toEncodedString(data, Charset.defaultCharset());
            DeserializationErrorMessage dummy = DeserializationErrorMessage.createFromException(
                    topic, base, dataAsSting, e);
            log.debug("{}", dummy.getData().getErrorMessage(), e);
            return dummy;
        }
    }

    @Override
    public void close() {
        // No-op
    }
}
