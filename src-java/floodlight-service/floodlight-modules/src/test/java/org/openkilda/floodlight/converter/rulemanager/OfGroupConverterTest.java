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

import static org.junit.Assert.assertEquals;

import org.openkilda.model.GroupId;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply.Builder;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OfGroupConverterTest {

    private static final int GROUP_ID = 12;

    private List<OFAction> getActions(OFFactoryVer13 factory, int portNumber) {
        List<OFAction> list = new ArrayList<>();
        list.add(factory.actions().buildOutput().setPort(OFPort.of(portNumber)).build());
        return list;
    }

    private OFGroupDescStatsEntry getOfGroupEntry(OFFactoryVer13 factory) {
        OFGroupDescStatsEntry.Builder entryBuilder = factory.buildGroupDescStatsEntry();
        List<OFBucket> buckets = new ArrayList<>();
        buckets.add(factory.buildBucket().setWatchPort(OFPort.ANY)
                .setWatchGroup(OFGroup.ALL)
                .setActions(getActions(factory, 1))
                .build());

        buckets.add(factory.buildBucket().setWatchPort(OFPort.ANY)
                .setWatchGroup(OFGroup.ALL)
                .setActions(getActions(factory, 2))
                .build());

        return entryBuilder.setGroup(OFGroup.of(GROUP_ID))
                .setGroupType(OFGroupType.ALL)
                .setBuckets(buckets).build();
    }

    @Test
    public void testConvertToGroupSpeakerData() {
        OFFactoryVer13 factory = new OFFactoryVer13();

        Builder builder = factory.buildGroupDescStatsReply();
        List<OFGroupDescStatsEntry> entries = new ArrayList<>();
        entries.add(getOfGroupEntry(factory));
        builder.setEntries(entries);

        List<GroupSpeakerData> groupSpeakerDataList = OfGroupConverter.INSTANCE.convertToGroupSpeakerData(
                builder.build());
        assertEquals(1, groupSpeakerDataList.size());
        GroupSpeakerData groupSpeakerData = groupSpeakerDataList.get(0);
        assertEquals(new GroupId(GROUP_ID), groupSpeakerData.getGroupId());
        assertEquals(GroupType.ALL, groupSpeakerData.getType());
        List<Bucket> buckets = groupSpeakerData.getBuckets();

        Set<Bucket> expectedBuckets = new HashSet<>();
        expectedBuckets.add(Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ALL)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(2, null)))).build());

        expectedBuckets.add(Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ALL)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(1, null)))).build());
        assertEquals(expectedBuckets, new HashSet<>(buckets));
    }

    @Test
    public void testConvertInstallGroupCommand() {
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ALL)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(2, null)))).build());

        buckets.add(Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ALL)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(1, null)))).build());


        GroupSpeakerData groupSpeakerData = GroupSpeakerData.builder().groupId(new GroupId(GROUP_ID))
                .type(GroupType.ALL)
                .buckets(buckets)
                .build();
        OFFactoryVer13 factory = new OFFactoryVer13();

        OFGroupAdd ofGroupAdd = OfGroupConverter.INSTANCE.convertInstallGroupCommand(groupSpeakerData, factory);

        assertEquals(OFGroup.of(GROUP_ID), ofGroupAdd.getGroup());
        assertEquals(OFGroupType.ALL, ofGroupAdd.getGroupType());
        assertEquals(2, ofGroupAdd.getBuckets().size());

        List<OFBucket> expectedBuckets = new ArrayList<>();
        expectedBuckets.add(factory.buildBucket().setWatchPort(OFPort.ANY)
                .setWatchGroup(OFGroup.ALL)
                .setActions(getActions(factory, 2))
                .build());

        expectedBuckets.add(factory.buildBucket().setWatchPort(OFPort.ANY)
                .setWatchGroup(OFGroup.ALL)
                .setActions(getActions(factory, 1))
                .build());
        assertEquals(expectedBuckets, ofGroupAdd.getBuckets());
    }

    @Test
    public void testConvertDeleteGroupCommand() {
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ALL)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(2, null)))).build());

        buckets.add(Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ALL)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(1, null)))).build());


        GroupSpeakerData groupSpeakerData = GroupSpeakerData.builder().groupId(new GroupId(GROUP_ID))
                .type(GroupType.ALL)
                .buckets(buckets)
                .build();
        OFFactoryVer13 factory = new OFFactoryVer13();

        OFGroupDelete ofGroupDelete = OfGroupConverter.INSTANCE.convertDeleteGroupCommand(groupSpeakerData, factory);


        assertEquals(OFGroup.of(GROUP_ID), ofGroupDelete.getGroup());
        assertEquals(OFGroupType.ALL, ofGroupDelete.getGroupType());
    }

}
