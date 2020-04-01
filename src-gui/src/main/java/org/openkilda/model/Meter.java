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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
 
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Meter {
    
    @JsonProperty("meter_id")
    private Integer meterId;
    
    @JsonProperty("rate")
    private String rate;
    
    @JsonProperty("burst_size")
    private Integer burstSize;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("flags")
    private List<String> flags;
}
