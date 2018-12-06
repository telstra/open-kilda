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

package org.openkilda.persistence.converters;

import static java.lang.String.format;

import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.ogm.typeconversion.AttributeConverter;

import java.io.IOException;

/**
 * Converter to convert {@link FlowPath} to JSON String representation and back.
 */
public class FlowPathConverter implements AttributeConverter<FlowPath, String> {

    @Override
    public String toGraphProperty(FlowPath value) {
        if (value == null) {
            return null;
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new PersistenceException(format("Unable to convert Flow Path: %s", value), ex);
        }
    }

    @Override
    public FlowPath toEntityAttribute(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readValue(value, FlowPath.class);
        } catch (IOException ex) {
            throw new PersistenceException(format("Unable to convert to Flow Path: %s", value), ex);
        }
    }
}
