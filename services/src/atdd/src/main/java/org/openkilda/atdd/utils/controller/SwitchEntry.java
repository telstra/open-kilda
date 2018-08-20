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

package org.openkilda.atdd.utils.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwitchEntry implements Serializable {

    @JsonProperty("version")
    private String version;

    @JsonProperty("port_desc")
    private List<PortEntry> portEntries;

    @JsonCreator
    public SwitchEntry(
            @JsonProperty("version") String version,
            @JsonProperty("port_desc") List<PortEntry> portEntries) {
        this.version = version;
        this.portEntries = portEntries;
    }
    
    public String getVersion() {
        return version;
    }
    
    public List<PortEntry> getPortEntries() {
        return portEntries;
    }
}
