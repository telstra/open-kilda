/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkPropsDto {

    @JsonProperty("src_switch")
    private String srcSwitch;
    @JsonProperty("src_port")
    private Integer srcPort;
    @JsonProperty("dst_switch")
    private String dstSwitch;
    @JsonProperty("dst_port")
    private Integer dstPort;
    @JsonProperty("props")
    private Map<String, String> props;

    public LinkPropsDto(@JsonProperty("src_switch") String srcSwitch, @JsonProperty("src_port") Integer srcPort,
                        @JsonProperty("dst_switch") String dstSwitch, @JsonProperty("dst_port") Integer dstPort,
                        @JsonProperty("props") Map<String, String> props) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = dstSwitch;
        this.dstPort = dstPort;
        this.props = props;
    }

    public String getSrcSwitch() {
        return srcSwitch;
    }

    public Integer getSrcPort() {
        return srcPort;
    }

    public String getDstSwitch() {
        return dstSwitch;
    }

    public Integer getDstPort() {
        return dstPort;
    }

    public Map<String, String> getProps() {
        return new HashMap<>(props);
    }

    public String getProperty(String key) {
        return props.get(key);
    }
}
