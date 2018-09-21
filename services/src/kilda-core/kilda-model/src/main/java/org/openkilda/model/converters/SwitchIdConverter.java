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

package org.openkilda.model.converters;

import org.openkilda.model.SwitchId;

import org.neo4j.ogm.typeconversion.AttributeConverter;

/**
 * Converter to convert {@link SwitchId} to {@link Long}.
 */
public class SwitchIdConverter implements AttributeConverter<SwitchId, Long> {

    @Override
    public Long toGraphProperty(SwitchId value) {
        if (value == null)
            return null;
        return value.toLong();
    }

    @Override
    public SwitchId toEntityAttribute(Long value) {
        if (value == null)
            return null;
        return new SwitchId(value.longValue());
    }
}
