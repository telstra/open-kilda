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

package org.openkilda.messaging.info.discovery;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = false)
public class RemoveIslDefaultRulesResult extends InfoData {

    @JsonProperty("src_switch")
    private SwitchId srcSwitch;

    @JsonProperty("dst_switch")
    private SwitchId dstSwitch;

    @JsonProperty("src_port")
    private int srcPort;

    @JsonProperty("dst_port")
    private int dstPort;


    @JsonProperty("success")
    private boolean success;


    @JsonCreator
    public RemoveIslDefaultRulesResult(@JsonProperty("src_switch") SwitchId srcSwitch,
                                       @JsonProperty("src_port") int srcPort,
                                       @JsonProperty("dst_switch") SwitchId dstSwitch,
                                       @JsonProperty("dst_port") int dstPort,
                                       @JsonProperty("success") boolean success) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = dstSwitch;
        this.dstPort = dstPort;
        this.success = success;

    }
}
