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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.HashMap;
import java.util.Map;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkProps {

    private static final String DEFAULT = "";
    
    @JsonProperty("src_switch")
    private String srcSwitch = DEFAULT;
    
    @JsonProperty("src_port")
    private String srcPort = DEFAULT;
    
    @JsonProperty("dst_switch")
    private String dstSwitch = DEFAULT;
    
    @JsonProperty("dst_port")
    private String dstPort = DEFAULT;
    
    @JsonProperty("props")
    private Map<String, String> props = new HashMap<>();

    /**
     * Creates an empty link properties.
     */
    public LinkProps() {
    }

    /**
     * Creates a copy of link properties.
     */
    public LinkProps(Map<String, String> props) {
        this.props = new HashMap<>(props);
    }

    public String getProperty(String key) {
        return props.getOrDefault(key, DEFAULT);
    }

    public LinkProps setProperty(String key, String value) {
        props.put(key, value);
        return this;
    }
    
    @JsonProperty("src_switch")
    public String getSrcSwitch() {
        return srcSwitch;
    }
    
    @JsonProperty("src_switch")
    public void setSrcSwitch(String srcSwitch) {
        this.srcSwitch = srcSwitch;
    }
    
    @JsonProperty("src_port")
    public String getSrcPort() {
        return srcPort;
    }
    
    @JsonProperty("src_port")
    public void setSrcPort(String srcPort) {
        this.srcPort = srcPort;
    }
    
    @JsonProperty("dst_switch")
    public String getDstSwitch() {
        return dstSwitch;
    }
    
    @JsonProperty("dst_switch")
    public void setDstSwitch(String dstSwitch) {
        this.dstSwitch = dstSwitch;
    }
    
    @JsonProperty("dst_port")
    public String getDstPort() {
        return dstPort;
    }
    
    @JsonProperty("dst_port")
    public void setDstPort(String dstPort) {
        this.dstPort = dstPort;
    }
}
