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

package org.openkilda.messaging.info.switches;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public class SwitchPortsDescription extends InfoData {

    @JsonProperty("version")
    private String version;

    @JsonProperty("port_desc")
    private List<PortDescription> portsDescription;

    @JsonCreator
    public SwitchPortsDescription(
            @JsonProperty("version") String version,
            @JsonProperty("port_desc") List<PortDescription> portsDescription) {
        this.version = version;
        this.portsDescription = portsDescription;
    }
}
