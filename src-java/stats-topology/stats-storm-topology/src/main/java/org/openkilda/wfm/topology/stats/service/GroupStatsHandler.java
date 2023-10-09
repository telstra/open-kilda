/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.service;

import static org.openkilda.model.GroupId.ROUND_TRIP_LATENCY_GROUP_ID;

import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.CommonHaFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyGroupDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyMeterDescriptor;
import org.openkilda.wfm.topology.stats.model.EndpointFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.HaFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MirrorGroupDescriptor;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GroupStatsHandler extends BaseStatsEntryHandler {
    private final GroupStatsEntry statsEntry;

    private GroupStatsHandler(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, GroupStatsEntry statsEntry) {
        super(meterEmitter, switchId, timestamp);
        this.statsEntry = statsEntry;
    }

    public static void apply(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, GroupStatsEntry statsEntry,
            KildaEntryDescriptor descriptor) {
        GroupStatsHandler handler = new GroupStatsHandler(meterEmitter, switchId, timestamp, statsEntry);
        handler.handle(descriptor);
    }

    @Override
    public void handleStatsEntry(CommonFlowDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(EndpointFlowDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(YFlowDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(YFlowSubDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(DummyFlowDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(StatVlanDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(HaFlowDescriptor descriptor) {
        handleStatsEntry((CommonHaFlowDescriptor) descriptor);
    }

    @Override
    public void handleStatsEntry(DummyMeterDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(MirrorGroupDescriptor descriptor) {
        // nothing to do here, since do not need to write stats for now
    }

    @Override
    public void handleStatsEntry(DummyGroupDescriptor descriptor) {
        if (ROUND_TRIP_LATENCY_GROUP_ID.getValue() != statsEntry.getGroupId()) {
            log.warn("Missed cache for switch '{}' groupId '{}'", switchId, statsEntry.getGroupId());
        }
    }

    @Override
    public void handleStatsEntry(CommonHaFlowDescriptor descriptor) {
        if (MeasurePoint.HA_FLOW_Y_POINT == descriptor.getMeasurePoint()) {
            TagsFormatter tags = initTags();
            tags.addHaFlowIdTag(descriptor.getHaFlowId());
            emitHaFlowYPointGroupPoints(tags);
        }
    }

    private void emitHaFlowYPointGroupPoints(TagsFormatter tagsFormatter) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("haflow.group.ypoint."), timestamp,
                statsEntry.getPacketsInCount(), statsEntry.getByteInCount(), tagsFormatter.getTags());
    }

    private TagsFormatter initTags() {
        TagsFormatter tags = new TagsFormatter();
        tags.addSwitchIdTag(switchId);
        tags.addGroupIdTag(statsEntry.getGroupId());
        return tags;
    }
}
