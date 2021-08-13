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

package org.openkilda.persistence.ferma.frames.converters;

import org.openkilda.model.FlowApplication;

/**
 * Case-insensitive converter to convert {@link FlowApplication} to {@link String} and back.
 */
public class FlowApplicationConverter implements AttributeConverter<FlowApplication, String> {
    public static final FlowApplicationConverter INSTANCE = new FlowApplicationConverter();

    @Override
    public String toGraphProperty(FlowApplication value) {
        if (value == null) {
            return null;
        }
        return value.name().toLowerCase();
    }

    @Override
    public FlowApplication toEntityAttribute(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return FlowApplication.valueOf(value.toUpperCase());
    }
}
