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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpdateIngressAndEgressFlows extends CommandData {

    @JsonProperty("ingress_flow")
    private InstallIngressFlow installIngressFlow;

    @JsonProperty("egress_flow")
    private InstallEgressFlow installEgressFlow;

    @JsonProperty("telescope_port")
    private int telescopePort;

    @JsonProperty("telescope_cookie")
    private long telescopeCookie;

    @JsonCreator
    public UpdateIngressAndEgressFlows(@JsonProperty("ingress_flow") InstallIngressFlow installIngressFlow,
                                       @JsonProperty("egress_flow") InstallEgressFlow installEgressFlow,
                                       @JsonProperty("telescope_port") int telescopePort,
                                       @JsonProperty("telescope_cookie") long telescopeCookie) {
        this.installIngressFlow = installIngressFlow;
        this.installEgressFlow = installEgressFlow;
        this.telescopePort = telescopePort;
        this.telescopeCookie = telescopeCookie;
    }
}
