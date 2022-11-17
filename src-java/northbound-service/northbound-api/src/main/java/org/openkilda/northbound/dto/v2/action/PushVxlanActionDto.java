/* Copyright 2021 Telstra Open Source
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

package org.openkilda.northbound.dto.v2.action;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@JsonNaming(SnakeCaseStrategy.class)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class PushVxlanActionDto extends BaseAction {

    String srcMacAddress;
    String dstMacAddress;
    String srcIpv4Address;
    String dstIpv4Address;
    int udpSrc;
    int vni;

    @JsonCreator
    public PushVxlanActionDto(@JsonProperty("action_type") @NonNull String actionType,
                              @JsonProperty("src_mac_address") String srcMacAddress,
                              @JsonProperty("dst_mac_address") String dstMacAddress,
                              @JsonProperty("src_ipv4_address") String srcIpv4Address,
                              @JsonProperty("dst_ipv4_address") String dstIpv4Address,
                              @JsonProperty("upd_src") int udpSrc,
                              @JsonProperty("vni") int vni) {
        super(actionType);
        this.srcMacAddress = srcMacAddress;
        this.dstMacAddress = dstMacAddress;
        this.srcIpv4Address = srcIpv4Address;
        this.dstIpv4Address = dstIpv4Address;
        this.udpSrc = udpSrc;
        this.vni = vni;
    }
}
