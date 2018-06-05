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

package org.openkilda.northbound.dto.links;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(exclude = "props")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LinkPropsDto {

    private static final String DEFAULT = "";
    private String srcSwitch = DEFAULT;
    private String srcPort = DEFAULT;
    private String dstSwitch = DEFAULT;
    private String dstPort = DEFAULT;
    @JsonProperty("props")
    private Map<String,String> props = new HashMap<>();

    /**
     * Creates an empty link properties.
     */
    public LinkPropsDto(){
    }

    /**
     * Creates a copy of link properties
     */
    public LinkPropsDto(Map<String,String> props){
        this.props = new HashMap<>(props);
    }

    public String getProperty(String key) {
        return props.getOrDefault(key, DEFAULT);
    }

    public LinkPropsDto setProperty(String key, String value) {
        props.put(key, value);
        return this;
    }
}
