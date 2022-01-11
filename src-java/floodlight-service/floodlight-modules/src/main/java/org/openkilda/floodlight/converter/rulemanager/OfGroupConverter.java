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

package org.openkilda.floodlight.converter.rulemanager;

import org.openkilda.model.GroupId;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFBucket.Builder;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public class OfGroupConverter {
    public static final OfGroupConverter INSTANCE = Mappers.getMapper(OfGroupConverter.class);

    /**
     * Convert stats reply.
     */
    public List<GroupSpeakerData> convertToGroupSpeakerData(OFGroupDescStatsReply statsReply) {
        List<GroupSpeakerData> commandData = new ArrayList<>();
        for (OFGroupDescStatsEntry entry : statsReply.getEntries()) {
            GroupId groupId = new GroupId(entry.getGroup().getGroupNumber());
            GroupType type = fromOfGroupType(entry.getGroupType());
            List<Bucket> buckets = new ArrayList<>();
            List<OFBucket> ofBuckets = entry.getBuckets();
            for (OFBucket bucket : ofBuckets) {
                buckets.add(fromOfBucket(bucket));
            }
            commandData.add(GroupSpeakerData.builder().groupId(groupId).type(type).buckets(buckets).build());
        }
        return commandData;
    }

    /**
     * Convert group command data into OfGroupMod representation.
     */
    public OFGroupAdd convertInstallGroupCommand(GroupSpeakerData commandData, OFFactory ofFactory) {
        List<OFBucket> buckets = commandData.getBuckets().stream()
                .map(x -> toOfBucket(ofFactory, x)).collect(Collectors.toList());
        return ofFactory.buildGroupAdd()
                .setGroup(OFGroup.of((int) commandData.getGroupId().getValue()))
                .setGroupType(toOfGroupType(commandData.getType()))
                .setBuckets(buckets)
                .build();

    }

    /**
     * Convert group command data into OfGroupMod representation.
     */
    public OFGroupDelete convertDeleteGroupCommand(GroupSpeakerData commandData, OFFactory ofFactory) {
        return ofFactory.buildGroupDelete()
                .setGroup(OFGroup.of((int) commandData.getGroupId().getValue()))
                .setGroupType(toOfGroupType(commandData.getType()))
                .build();
    }

    private OFBucket toOfBucket(OFFactory ofFactory, Bucket bucket) {
        List<OFAction> ofActions = OfInstructionsConverter.INSTANCE.convertActions(bucket.getWriteActions(),
                ofFactory);
        Builder builder = ofFactory.buildBucket()
                .setActions(ofActions)
                .setWatchGroup(toOfGroup(bucket.getWatchGroup()));
        if (bucket.getWatchPort() != null) {
            builder.setWatchPort(toOfWatchPort(bucket.getWatchPort()));
        }
        return builder.build();
    }

    private Bucket fromOfBucket(OFBucket ofBucket) {
        WatchGroup watchGroup = fromOfGroup(ofBucket.getWatchGroup());
        WatchPort watchPort = fromOfPort(ofBucket.getWatchPort());
        Set<Action> actions = new HashSet<>();
        for (OFAction ofAction : ofBucket.getActions()) {
            actions.add(OfInstructionsConverter.INSTANCE.convertToRuleManagerAction(ofAction));
        }
        return Bucket.builder().watchGroup(watchGroup).watchPort(watchPort).writeActions(actions).build();
    }

    private OFPort toOfWatchPort(WatchPort watchPort) {
        switch (watchPort) {
            case ANY:
                return OFPort.ANY;
            default:
                throw new IllegalArgumentException(String.format("Unknown watch port %s", watchPort));
        }
    }

    private WatchPort fromOfPort(OFPort watchPort) {
        if (watchPort.equals(OFPort.ANY)) {
            return WatchPort.ANY;
        } else {
            throw new IllegalArgumentException(String.format("Unknown watch port %s", watchPort));
        }
    }

    private OFGroup toOfGroup(WatchGroup watchGroup) {
        switch (watchGroup) {
            case ANY:
                return OFGroup.ANY;
            case ALL:
                return OFGroup.ALL;
            default:
                throw new IllegalArgumentException(String.format("Unknown watch group %s", watchGroup));
        }
    }

    private WatchGroup fromOfGroup(OFGroup watchGroup) {
        if (watchGroup.equals(OFGroup.ANY)) {
            return WatchGroup.ANY;
        } else if (watchGroup.equals(OFGroup.ALL)) {
            return WatchGroup.ALL;
        } else {
            throw new IllegalArgumentException(String.format("Unknown watch group %s", watchGroup));
        }
    }

    private OFGroupType toOfGroupType(GroupType groupType) {
        switch (groupType) {
            case ALL:
                return OFGroupType.ALL;
            default:
                throw new IllegalArgumentException(String.format("Unknown group type %s", groupType));
        }
    }

    private GroupType fromOfGroupType(OFGroupType groupType) {
        switch (groupType) {
            case ALL:
                return GroupType.ALL;
            default:
                throw new IllegalArgumentException(String.format("Unknown group type %s", groupType));
        }
    }

}
