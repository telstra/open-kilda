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

import org.openkilda.bluegreen.kafka.TransportErrorReport;
import org.openkilda.messaging.SerializationUtils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.TransportErrorWrapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class InfoDataDeserializer implements Deserializer<InfoData> {

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No-op
    }

    @Override
    public InfoData deserialize(String topic, byte[] data) {
        Class<InfoData> base = InfoData.class;
        try {
            return SerializationUtils.MAPPER.readValue(data, base);
        } catch (IOException e) {
            TransportErrorReport errorReport = TransportErrorReport.createFromException(
                    topic, base, data, e);
            return new TransportErrorWrapper(errorReport);
        }
    }

    @Override
    public void close() {
        // No-op
    }
}
