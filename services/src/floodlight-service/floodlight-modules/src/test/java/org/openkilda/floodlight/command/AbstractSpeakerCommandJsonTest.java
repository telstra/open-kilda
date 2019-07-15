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

package org.openkilda.floodlight.command;

import org.openkilda.messaging.AbstractMessage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public abstract class AbstractSpeakerCommandJsonTest<T extends AbstractMessage> {
    private final ObjectMapper jsonMapper = new ObjectMapper();

    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    @Test
    public void testJsonDecoding() throws Exception {
        T request = makeRequest();

        String json = jsonMapper.writeValueAsString(request);
        log.info("JSON representation of {} => {}", request.getClass().getName(), json);

        TypeReference<SpeakerCommand<SpeakerCommandReport>> commandType
                = new TypeReference<SpeakerCommand<SpeakerCommandReport>>() {};
        SpeakerCommand<SpeakerCommandReport> command = jsonMapper.readValue(json, commandType);

        verify(request, command);
    }

    protected abstract void verify(T request, SpeakerCommand<? extends SpeakerCommandReport> rawCommand);

    protected abstract T makeRequest();
}
