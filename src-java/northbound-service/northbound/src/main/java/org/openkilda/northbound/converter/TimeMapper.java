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
import org.mapstruct.Named;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Mapper(componentModel = "spring")
public abstract class TimeMapper {
    /**
     * Convert {@link String} to {@link Instant}.
     */
    public Instant mapInstant(String value) {
        if (value == null) {
            return null;
        }

        return Instant.parse(value);
    }

    /**
     * Format {@link Instant} into NB API representation.
     */
    public String mapInstant(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(timestamp);
    }

    /**
         * Convert milliseconds into nanoseconds.
         */
    @Named("timeMillisToNanos")
    public Long timeMillisToNanos(Long millis) {
        if (millis == null) {
            return null;
        }
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    /**
     * Convert nanoseconds into milliseconds.
     */
    @Named("timeNanosToMillis")
    public Long timeNanosToMillis(Long nanos) {
        if (nanos == null) {
            return null;
        }
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    public Duration mapMillis(long millis) {
        return Duration.ofMillis(millis);
    }

    /**
     * Convert {@link Duration} into {@link long}.
     */
    public long mapMillis(Duration millis) {
        if (millis == null) {
            return 0;
        }
        return millis.toMillis();
    }
}
