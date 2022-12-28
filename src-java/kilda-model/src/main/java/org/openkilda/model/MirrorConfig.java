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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@EqualsAndHashCode(exclude = {"addNewGroup"})
public class MirrorConfig implements Serializable {

    @JsonProperty("group_id")
    GroupId groupId;

    @JsonProperty("flow_port")
    int flowPort;

    @Builder.Default
    @JsonProperty("mirror_data_set")
    Set<MirrorConfigData> mirrorConfigDataSet = new HashSet<>();

    @JsonProperty("add_new_group")
    boolean addNewGroup;

    @JsonCreator
    public MirrorConfig(@JsonProperty("group_id") GroupId groupId,
                        @JsonProperty("flow_port") int flowPort,
                        @JsonProperty("mirror_data_set") Set<MirrorConfigData> mirrorConfigDataSet,
                        @JsonProperty("add_new_group") boolean addNewGroup) {
        this.groupId = groupId;
        this.flowPort = flowPort;
        this.mirrorConfigDataSet = mirrorConfigDataSet;
        this.addNewGroup = addNewGroup;
    }

    @Value
    public static class MirrorConfigData implements Serializable {
        @JsonProperty("out_port")
        int outPort;

        @JsonProperty("push_vlan")
        Integer pushVlan;

        @JsonProperty("push_vxlan")
        PushVxlan pushVxlan;

        @JsonCreator
        public MirrorConfigData(@JsonProperty("out_port") int outPort,
                                @JsonProperty("push_vlan") Integer pushVlan,
                                @JsonProperty("push_vxlan") PushVxlan pushVxlan) {
            this.outPort = outPort;
            this.pushVlan = pushVlan;
            this.pushVxlan = pushVxlan;
        }
    }

    @Value
    public static class PushVxlan implements Serializable {
        @JsonProperty("vni")
        int vni;
        @JsonProperty("destination_mac")
        MacAddress destinationMac;

        @JsonCreator
        public PushVxlan(@JsonProperty("vni") int vni,
                         @JsonProperty("destination_mac") MacAddress destinationMac) {
            this.vni = vni;
            this.destinationMac = destinationMac;
        }
    }
}
