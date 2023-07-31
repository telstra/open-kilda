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

package org.openkilda.persistence.ferma.frames.converters;

import org.openkilda.model.history.HaFlowEventDump.HaFlowPathDump;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.shaded.jackson.core.JsonProcessingException;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
public class HaFlowPathDumpConverter implements AttributeConverter<HaFlowPathDump, String> {
    @Override
    public String toGraphProperty(HaFlowPathDump value) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("An exception occurred when attempting to serialize HaSubFlowDumpWrapper", e);
            return "";
        }
    }

    @Override
    public HaFlowPathDump toEntityAttribute(String value) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(value, HaFlowPathDump.class);
        } catch (IOException e) {
            log.error("An exception occurred when attempting to deserialize HaSubFlowDumpWrapper", e);
            return HaFlowPathDump.builder().build();
        }
    }
}
