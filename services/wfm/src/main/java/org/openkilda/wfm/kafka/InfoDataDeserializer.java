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

import static java.lang.String.format;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.errors.SerializationException;

import java.io.IOException;
import java.nio.charset.Charset;

@Slf4j
public class InfoDataDeserializer extends Deserializer<InfoData> {

    @Override
    public InfoData deserialize(String topic, byte[] data) {
        try {
            return Utils.MAPPER.readValue(data, InfoData.class);
        } catch (IOException e) {
            log.error(format("Failed to deserialize data: %s from topic %s",
                    StringUtils.toEncodedString(data, Charset.defaultCharset()), topic), e);
            throw new SerializationException(e.getMessage());
        }
    }
}
