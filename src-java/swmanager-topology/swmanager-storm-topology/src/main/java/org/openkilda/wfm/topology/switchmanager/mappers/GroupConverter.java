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

import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry.BucketEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry.BucketEntry.BucketEntryBuilder;
import org.openkilda.rulemanager.GroupSpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.group.Bucket;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public abstract class GroupConverter {
    public static final GroupConverter INSTANCE = Mappers.getMapper(GroupConverter.class);

    /**
     * Converts OpenFlow group representation.
     */
    public GroupInfoEntry convert(GroupSpeakerCommandData groupSpeakerCommandData) {
        return GroupInfoEntry.builder()
                .groupId(groupSpeakerCommandData.getGroupId().intValue())
                .groupBuckets(convertBuckets(groupSpeakerCommandData.getBuckets()))
                .build();
    }

    private List<BucketEntry> convertBuckets(List<Bucket> buckets) {
        return buckets.stream().map(this::convertBucket).collect(Collectors.toList());
    }

    private BucketEntry convertBucket(Bucket bucket) {
        BucketEntryBuilder builder = BucketEntry.builder();
        bucket.getWriteActions().forEach(action -> processAction(builder, action));
        return builder.build();
    }

    private void processAction(BucketEntryBuilder builder, Action action) {
        switch (action.getType()) {
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                builder.port(portOutAction.getPortNumber().getPortNumber());
                break;
                //todo: add other actions
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }
}
