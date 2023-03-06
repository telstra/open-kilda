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

package org.openkilda.rulemanager.action;

import static org.openkilda.rulemanager.action.ActionType.PUSH_VXLAN_NOVIFLOW;
import static org.openkilda.rulemanager.action.ActionType.PUSH_VXLAN_OVS;

import org.openkilda.model.IPv4Address;
import org.openkilda.model.MacAddress;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Sets;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Set;

@Value
@JsonSerialize
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class PushVxlanAction implements Action {
    private static final Set<ActionType> VALID_TYPES = Sets.newHashSet(PUSH_VXLAN_NOVIFLOW, PUSH_VXLAN_OVS);

    @NonNull
    ActionType type;
    int vni;
    MacAddress srcMacAddress;
    MacAddress dstMacAddress;
    IPv4Address srcIpv4Address;
    IPv4Address dstIpv4Address;
    int udpSrc;

    @Builder
    @JsonCreator
    public PushVxlanAction(@JsonProperty("type") @NonNull ActionType type,
                           @JsonProperty("vni") int vni,
                           @JsonProperty("src_mac_address") MacAddress srcMacAddress,
                           @JsonProperty("dst_mac_address") MacAddress dstMacAddress,
                           @JsonProperty("src_ipv4_address") IPv4Address srcIpv4Address,
                           @JsonProperty("dst_ipv4_address") IPv4Address dstIpv4Address,
                           @JsonProperty("udp_src") int udpSrc) {
        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    String.format("Type %s is invalid. Valid types: %s", type, VALID_TYPES));
        }
        this.type = type;
        this.vni = vni;
        this.srcMacAddress = srcMacAddress;
        this.dstMacAddress = dstMacAddress;
        this.srcIpv4Address = srcIpv4Address;
        this.dstIpv4Address = dstIpv4Address;
        this.udpSrc = udpSrc;
    }
}
