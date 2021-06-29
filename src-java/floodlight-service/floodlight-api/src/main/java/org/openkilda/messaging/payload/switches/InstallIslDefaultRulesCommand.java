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


package org.openkilda.messaging.payload.switches;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@EqualsAndHashCode(callSuper = false)
public class InstallIslDefaultRulesCommand extends CommandData {

    private static final long serialVersionUID = 7393431355263735216L;

    @JsonProperty("src_switch")
    private SwitchId srcSwitch;

    @JsonProperty("src_port")
    private int srcPort;

    @JsonProperty("dst_switch")
    private SwitchId dstSwitch;

    @JsonProperty("dst_port")
    private int dstPort;

    @JsonProperty("multitable_mode")
    private boolean multitableMode;

    @JsonProperty("server42_isl_rtt")
    private boolean server42IslRtt;

    @JsonProperty("server42_port")
    private Integer server42Port;

    @JsonCreator
    @Builder(toBuilder = true)
    public InstallIslDefaultRulesCommand(@JsonProperty("src_switch") final SwitchId srcSwitch,
                                         @JsonProperty("src_port") final int srcPort,
                                         @JsonProperty("dst_switch") final SwitchId dstSwitch,
                                         @JsonProperty("dst_port") final int dstPort,
                                         @JsonProperty("multitable_mode") boolean multitableMode,
                                         @JsonProperty("server42_isl_rtt") boolean server42IslRtt,
                                         @JsonProperty("server42_port") Integer server42Port) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = dstSwitch;
        this.dstPort = dstPort;
        this.multitableMode = multitableMode;
        this.server42IslRtt = server42IslRtt;
        this.server42Port = server42Port;
    }
}

