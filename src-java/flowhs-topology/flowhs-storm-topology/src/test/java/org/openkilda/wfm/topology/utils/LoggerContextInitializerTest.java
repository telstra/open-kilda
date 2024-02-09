/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.wfm.CommandContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public class LoggerContextInitializerTest {

    @Test
    public void shouldParseMessageAndExtractCorrelationId() {
        // given
        Tuple tuple = mock(Tuple.class);
        when(tuple.getFields()).thenReturn(new Fields("message"));

        String correlationId = String.format("test-%s", UUID.randomUUID());
        InfoMessage message = new InfoMessage(new FlowsResponse(Collections.emptyList()), System.currentTimeMillis(),
                correlationId);
        when(tuple.getValueByField(eq("message"))).thenReturn(message);

        // when
        Optional<CommandContext> result = LoggerContextInitializer.extract(tuple);

        //then
        assertTrue(result.isPresent());
        Assertions.assertEquals(correlationId, result.get().getCorrelationId());
    }

    @Test
    public void shouldParseJsonAndExtractCorrelationId() throws JsonProcessingException {
        // given
        Tuple tuple = mock(Tuple.class);
        when(tuple.getFields()).thenReturn(new Fields("message"));

        String correlationId = String.format("test-%s", UUID.randomUUID());
        InfoMessage message = new InfoMessage(new FlowsResponse(Collections.emptyList()), System.currentTimeMillis(),
                correlationId);
        ObjectMapper mapper = new ObjectMapper();
        when(tuple.getValueByField(eq("message"))).thenReturn(mapper.writeValueAsString(message));

        // when
        Optional<CommandContext> result = LoggerContextInitializer.extract(tuple);

        //then
        assertTrue(result.isPresent());
        Assertions.assertEquals(correlationId, result.get().getCorrelationId());
    }

    @Test
    public void shouldNotFailParsingJsonWithoutCorrId() {
        // given
        Tuple tuple = mock(Tuple.class);
        when(tuple.getFields()).thenReturn(new Fields("message"));

        when(tuple.getValueByField(eq("message"))).thenReturn("{fake:\"value\"}");

        // when
        Optional<CommandContext> result = LoggerContextInitializer.extract(tuple);

        //then
        Assertions.assertFalse(result.isPresent());
    }
}
