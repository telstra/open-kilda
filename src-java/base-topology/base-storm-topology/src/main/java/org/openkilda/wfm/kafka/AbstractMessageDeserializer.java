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

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.error.DeserializationErrorAbstractMessage;
import org.openkilda.wfm.topology.utils.SerializationUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

@Slf4j
public class AbstractMessageDeserializer implements Deserializer<AbstractMessage> {

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // nothing to do
    }

    @Override
    public AbstractMessage deserialize(String topic, byte[] data) {
        try {
            return SerializationUtils.MAPPER.readValue(data, AbstractMessage.class);
        } catch (IOException e) {
            String message = StringUtils.toEncodedString(data, Charset.defaultCharset());
            if (log.isDebugEnabled()) {
                log.debug(format("Failed to deserialize message: %s from topic %s", message, topic), e);
            }
            return new DeserializationErrorAbstractMessage("Failed to deserialize message", message);
        }
    }

    @Override
    public void close() {
        // nothing to do
    }
}
