/* Copyright 2021 Telstra Open Source
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

package org.openkilda.northbound.converter;

import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring")
public abstract class InstantMapper {
    /**
     * Convert {@link String} to {@link Instant}.
     */
    public Instant map(String value) {
        if (value == null) {
            return null;
        }

        return Instant.parse(value);
    }

    /**
     * Convert {@link Instant} to {@link String}.
     */
    public String map(Instant value) {
        if (value == null) {
            return null;
        }

        return DateTimeFormatter.ISO_INSTANT.format(value);
    }
}
