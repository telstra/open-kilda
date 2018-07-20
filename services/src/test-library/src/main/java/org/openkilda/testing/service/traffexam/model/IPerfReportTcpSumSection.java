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

package org.openkilda.testing.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class IPerfReportTcpSumSection {
    private double start;
    private double end;
    private double seconds;
    private long bytes;

    @JsonProperty("bits_per_second")
    private double bitsPerSecond;

    @JsonCreator
    public IPerfReportTcpSumSection(
            @JsonProperty("start") double start,
            @JsonProperty("end") double end,
            @JsonProperty("seconds") double seconds,
            @JsonProperty("bytes") long bytes,
            @JsonProperty("bits_per_second") double bitsPerSecond) {
        this.start = start;
        this.end = end;
        this.seconds = seconds;
        this.bytes = bytes;
        this.bitsPerSecond = bitsPerSecond;
    }
}
