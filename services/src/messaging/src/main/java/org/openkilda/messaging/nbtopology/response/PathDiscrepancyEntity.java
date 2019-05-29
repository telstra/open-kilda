/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.nbtopology.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class PathDiscrepancyEntity implements Serializable {

    @JsonProperty("rule")
    private String rule;

    @JsonProperty("field")
    private String field;

    @JsonProperty("expected_value")
    private String expectedValue;

    @JsonProperty("actual_value")
    private String actualValue;

    @JsonCreator
    public PathDiscrepancyEntity(@JsonProperty("rule") String rule,
                                 @JsonProperty("field") String field,
                                 @JsonProperty("expected_value") String expectedValue,
                                 @JsonProperty("actual_value") String actualValue) {
        this.rule = rule;
        this.field = field;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }
}
