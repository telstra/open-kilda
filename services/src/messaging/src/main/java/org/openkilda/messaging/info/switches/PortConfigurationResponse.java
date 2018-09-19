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
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
public class PortConfigurationResponse extends InfoData {

    private static final long serialVersionUID = 7332950619136252164L;

    @NonNull
    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("port_no")
    private int portNo;

    @JsonCreator
    public PortConfigurationResponse(@JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("port_no") int portNo) {
        this.switchId = switchId;
        this.portNo = portNo;
    }
}
