/* Copyright 2022 Telstra Open Source
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

package org.openkilda.testing.service.stats.model.victoriametrics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({"unix_time", "value"})
public class MatrixValueEntry {
    private final Instant timestamp;

    private final float value;

    @JsonCreator
    public MatrixValueEntry(
            @JsonProperty("unix_time") double unixTime,
            @JsonProperty("value") String rawValue) {
        long epochSeconds = (long) unixTime;

        timestamp = Instant.ofEpochSecond(epochSeconds);

        Duration millis = Duration.ofMillis((long) ((unixTime - epochSeconds) * 1000));
        timestamp.plus(millis);

        value = Float.parseFloat(rawValue);
    }
}
