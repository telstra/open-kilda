/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openkilda.wfm.topology.switchmanager.mappers.GroupEntryConverter.INSTANCE;

import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.model.GroupId;
import org.openkilda.model.IPv4Address;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GroupEntryConverterTest {
    public static final int GROUP_ID_VALUE = 1;
    public static final long SWITCH_ID_VALUE = 2L;
    public static final String MAC_ADDRESS_VALUE = "00:00:00:00:00:01";
    public static final String IPV4_ADDRESS_VALUE = "255.255.255.255";
    public static final int PORT_NUMBER_VALUE = 3;
    public static final int SET_FIELD_VALUE = 4;
    public static final int VNI_NOVIFLOW_ACTION_VALUE = 5;
    public static final int UDP_NOVIFLOW_ACTION_VALUE = 6;
    public static final int VNI_OVS_ACTION_VALUE = 7;
    public static final int UDP_OVS_ACTION_VALUE = 8;

    public static GroupId GROUP_ID = new GroupId(GROUP_ID_VALUE);
    public static GroupType GROUP_TYPE = GroupType.ALL;
    public static UUID RANDOM_UUID = UUID.randomUUID();
    public static SwitchId SWITCH_ID = new SwitchId(SWITCH_ID_VALUE);
    public static OfVersion OF_VERSION = OfVersion.OF_15;
    public static MacAddress MAC_ADDRESS = new MacAddress(MAC_ADDRESS_VALUE);
    public static IPv4Address IPV4_ADDRESS = new IPv4Address(IPV4_ADDRESS_VALUE);
    public static ProtoConstants.PortNumber PORT_NUMBER = new ProtoConstants.PortNumber(PORT_NUMBER_VALUE);

    public static PortOutAction PORT_OUT_ACTION = new PortOutAction(PORT_NUMBER);
    public static SetFieldAction SET_FIELD_ACTION = new SetFieldAction(SET_FIELD_VALUE, Field.VLAN_VID);

    public static PushVxlanAction PUSH_VXLAN_NOVIFLOW_ACTION = PushVxlanAction.builder()
            .type(ActionType.PUSH_VXLAN_NOVIFLOW)
            .vni(VNI_NOVIFLOW_ACTION_VALUE)
            .srcMacAddress(MAC_ADDRESS)
            .dstMacAddress(MAC_ADDRESS)
            .srcIpv4Address(IPV4_ADDRESS)
            .dstIpv4Address(IPV4_ADDRESS)
            .udpSrc(UDP_NOVIFLOW_ACTION_VALUE)
            .build();

    public static PushVxlanAction PUSH_VXLAN_OVS_ACTION = PushVxlanAction.builder()
            .type(ActionType.PUSH_VXLAN_OVS)
            .vni(VNI_OVS_ACTION_VALUE)
            .srcMacAddress(MAC_ADDRESS)
            .dstMacAddress(MAC_ADDRESS)
            .srcIpv4Address(IPV4_ADDRESS)
            .dstIpv4Address(IPV4_ADDRESS)
            .udpSrc(UDP_OVS_ACTION_VALUE)
            .build();

    public static GroupSpeakerData data;
    public static Set<Action> writeActions = new HashSet<>();
    public static List<Bucket> buckets = new LinkedList<>();

    private void initializeData() {
        writeActions.add(SET_FIELD_ACTION);
        writeActions.add(PORT_OUT_ACTION);
        writeActions.add(PUSH_VXLAN_OVS_ACTION);

        buckets.add(new Bucket(WatchGroup.ALL, WatchPort.ANY, writeActions));

        data = GroupSpeakerData.builder()
                .uuid(RANDOM_UUID)
                .switchId(SWITCH_ID)
                .dependsOn(Collections.emptyList())
                .ofVersion(OF_VERSION)
                .groupId(GROUP_ID)
                .type(GROUP_TYPE)
                .buckets(buckets)
                .build();
    }

    @Test
    public void mapGroupEntryTest() {
        initializeData();

        GroupInfoEntry entry = INSTANCE.toGroupEntry(data);

        assertEquals(GROUP_ID.intValue(), entry.getGroupId());
        GroupInfoEntry.BucketEntry testBucket = entry.getGroupBuckets().get(0);
        assertEquals(PORT_NUMBER.getPortNumber(), testBucket.getPort());
        assertEquals(SET_FIELD_ACTION.getValue(), (long) testBucket.getVlan());
        assertEquals(PUSH_VXLAN_OVS_ACTION.getVni(), (int) testBucket.getVni());
    }

}

