/* Copyright 2023 Telstra Open Source
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

import org.openkilda.messaging.SerializationUtils;
import org.openkilda.messaging.info.InfoData;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class InfoDataSerializer implements Serializer<InfoData> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No-op
    }

    @Override
    public byte[] serialize(String topic, InfoData data) {
        byte[] result = null;
        if (data != null) {
            try {
                result = SerializationUtils.MAPPER.writeValueAsBytes(data);
            } catch (IOException e) {
                log.error(
                        "Failed to serialize object belongs to {}: {}, for topic: {}",
                        data.getClass().getName(), data, topic);
                throw new SerializationException(e.getMessage(), e);
            }
        }
        return result;
    }

    @Override
    public void close() {
        // No-op
    }
}
