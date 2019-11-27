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

package org.openkilda.messaging.model.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KildaConfigurationDto implements Serializable {

    @JsonProperty("flow_encapsulation_type")
    private String flowEncapsulationType;

    @JsonProperty("use_multi_table")
    private Boolean useMultiTable;

    @JsonProperty("path_computation_strategy")
    private String pathComputationStrategy;

    @JsonCreator
    public KildaConfigurationDto(@JsonProperty("flow_encapsulation_type") String flowEncapsulationType,
                                 @JsonProperty("use_multi_table") Boolean useMultiTable,
                                 @JsonProperty("path_computation_strategy") String pathComputationStrategy) {
        this.flowEncapsulationType = flowEncapsulationType;
        this.useMultiTable = useMultiTable;
        this.pathComputationStrategy = pathComputationStrategy;
    }
}
