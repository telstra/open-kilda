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

package org.openkilda.persistence.converters;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.ExclusionCookie;

import org.neo4j.ogm.typeconversion.AttributeConverter;

/**
 * Converter to convert {@link Cookie} to {@link Long} and back.
 */
public class ExclusionCookieConverter implements AttributeConverter<ExclusionCookie, Long> {

    @Override
    public Long toGraphProperty(ExclusionCookie value) {
        if (value == null) {
            return null;
        }
        return value.getValue();
    }

    @Override
    public ExclusionCookie toEntityAttribute(Long value) {
        if (value == null) {
            return null;
        }
        return new ExclusionCookie(value);
    }
}
