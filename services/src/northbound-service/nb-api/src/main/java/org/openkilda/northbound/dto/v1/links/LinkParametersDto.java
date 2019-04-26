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

package org.openkilda.northbound.dto.v1.links;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LinkParametersDto implements Serializable {

    private String srcSwitch;
    private Integer srcPort;
    private String dstSwitch;
    private Integer dstPort;

    @Builder
    @JsonCreator
    public LinkParametersDto(@JsonProperty(value = "src_switch", required = true) String srcSwitch,
                             @JsonProperty(value = "src_port", required = true) Integer srcPort,
                             @JsonProperty(value = "dst_switch", required = true) String dstSwitch,
                             @JsonProperty(value = "dst_port", required = true) Integer dstPort) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = dstSwitch;
        this.dstPort = dstPort;
    }
}
