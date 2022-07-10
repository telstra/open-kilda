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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class MatrixResponseEntry {
    @JsonProperty("metric")
    private final MatrixMetric metric;

    @JsonProperty("values")
    private final List<MatrixValueEntry> values;

    @JsonCreator
    public MatrixResponseEntry(
            @JsonProperty("metric") MatrixMetric metric, @JsonProperty("values") List<MatrixValueEntry> values) {
        this.metric = metric;
        this.values = values;
    }
}
