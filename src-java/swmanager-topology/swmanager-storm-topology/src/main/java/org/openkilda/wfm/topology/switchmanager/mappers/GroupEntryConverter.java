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

package org.openkilda.wfm.topology.switchmanager.mappers;

import static java.lang.String.format;

import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2.BucketEntry;
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2.BucketEntry.BucketEntryBuilder;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public class GroupEntryConverter {
    public static final GroupEntryConverter INSTANCE = Mappers.getMapper(GroupEntryConverter.class);

    /**
     * Converts group representation.
     */
    public GroupInfoEntryV2 toGroupEntry(GroupSpeakerData groupSpeakerData) {
        return GroupInfoEntryV2.builder()
                .groupId((int) groupSpeakerData.getGroupId().getValue())
                .buckets(convertBuckets(groupSpeakerData.getBuckets()))
                .build();
    }

    private List<BucketEntry> convertBuckets(List<Bucket> buckets) {
        return buckets.stream().map(this::convertBucket).collect(Collectors.toList());
    }

    private BucketEntry convertBucket(Bucket bucket) {
        BucketEntryBuilder builder = BucketEntry.builder();
        bucket.getWriteActions().forEach(action -> addAction(builder, action));
        return builder.build();
    }

    private void addAction(BucketEntryBuilder builder, Action action) {
        switch (action.getType()) {
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                builder.port(convertPortNumber(portOutAction.getPortNumber()));
                return;
            case SET_FIELD:
                SetFieldAction setFieldAction = (SetFieldAction) action;
                if (setFieldAction.getField() == Field.VLAN_VID) {
                    builder.vlan((int) setFieldAction.getValue());
                }
                return;
            case PUSH_VXLAN_NOVIFLOW:
            case PUSH_VXLAN_OVS:
                PushVxlanAction pushVxlanAction = (PushVxlanAction) action;
                builder.vni(pushVxlanAction.getVni());
                return;
            default:
                // skip other actions
        }
    }

    private int convertPortNumber(PortNumber portNumber) {
        if (portNumber.getPortNumber() != 0) {
            return portNumber.getPortNumber();
        }
        switch (portNumber.getPortType()) {
            case IN_PORT:
                return -8;
            case CONTROLLER:
                return -3;
            case LOCAL:
                return -2;
            case ALL:
                return -4;
            case FLOOD:
                return -5;
            default:
                throw new IllegalStateException(format("Unknown port type %s", portNumber.getPortType()));
        }

    }
}
