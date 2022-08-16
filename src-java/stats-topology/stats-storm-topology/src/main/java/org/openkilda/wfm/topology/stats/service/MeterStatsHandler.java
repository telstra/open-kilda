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

package org.openkilda.wfm.topology.stats.service;

import static org.openkilda.model.MeterId.isMeterIdOfDefaultRule;

import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.ServiceCookie;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyMeterDescriptor;
import org.openkilda.wfm.topology.stats.model.EndpointFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MeterStatsHandler extends BaseStatsEntryHandler {
    private final MeterStatsEntry statsEntry;

    public static void apply(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, MeterStatsEntry statsEntry,
            KildaEntryDescriptor descriptor) {
        MeterStatsHandler handler = new MeterStatsHandler(meterEmitter, switchId, timestamp, statsEntry);
        handler.handle(descriptor);
    }

    private MeterStatsHandler(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, MeterStatsEntry statsEntry) {
        super(meterEmitter, switchId, timestamp);
        this.statsEntry = statsEntry;
    }

    @Override
    public void handleStatsEntry(CommonFlowDescriptor descriptor) {
        TagsFormatter tags = initTags();
        tags.addIsYFlowSubFlowTag(false);
        handleFlowStats(tags, descriptor.getCookie(), descriptor.getFlowId());
    }

    @Override
    public void handleStatsEntry(EndpointFlowDescriptor descriptor) {
        handleStatsEntry((CommonFlowDescriptor) descriptor);
    }

    @Override
    public void handleStatsEntry(YFlowDescriptor descriptor) {
        TagsFormatter tags = initTags();
        tags.addYFlowIdTag(descriptor.getYFlowId());
        switch (descriptor.getMeasurePoint()) {
            case Y_FLOW_SHARED:
                emitYFlowSharedMeterPoints(tags);
                break;
            case Y_FLOW_Y_POINT:
                emitYFlowYPointMeterPoints(tags);
                break;
            default:
                // nothing to do here
        }
    }

    @Override
    public void handleStatsEntry(YFlowSubDescriptor descriptor) {
        TagsFormatter tags = initTags();
        tags.addIsYFlowSubFlowTag(true);
        tags.addYFlowIdTag(descriptor.getYFlowId());
        handleFlowStats(tags, descriptor.getCookie(), descriptor.getSubFlowId());
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
    public void handleStatsEntry(DummyMeterDescriptor descriptor) {
        TagsFormatter tags = initTags();
        if (isMeterIdOfDefaultRule(statsEntry.getMeterId())) {
            tags.addCookieHexTag(new ServiceCookie(new MeterId(statsEntry.getMeterId())));
            emitServiceMeterPoints(tags);
        } else {
            handleFlowStats(tags, null, null);
            log.warn("Missed cache for switch '{}' meterId '{}'", switchId, statsEntry.getMeterId());
        }
    }

    private void handleFlowStats(TagsFormatter tags, FlowSegmentCookie cookie, String flowId) {
        tags.addFlowIdTag(flowId);
        tags.addCookieTag(cookie);

        FlowPathDirection direction = null;
        if (cookie != null) {
            direction = cookie.getDirection();
            if (direction == null) {
                log.warn(
                        "Unable to extract flow direction from cookie {} while processing stats of {}", cookie, flowId);
            }
        }
        tags.addDirectionTag(direction);

        emitFlowMeterPoints(tags);
    }

    private void emitServiceMeterPoints(TagsFormatter tagsFormatter) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("switch.flow.system.meter."), timestamp,
                statsEntry.getPacketsInCount(), statsEntry.getByteInCount(), tagsFormatter.getTags());
    }

    private void emitFlowMeterPoints(TagsFormatter tagsFormatter) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("flow.meter."), timestamp,
                statsEntry.getPacketsInCount(), statsEntry.getByteInCount(), tagsFormatter.getTags());
    }

    private void emitYFlowSharedMeterPoints(TagsFormatter tagsFormatter) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("yFlow.meter.shared."), timestamp,
                statsEntry.getPacketsInCount(), statsEntry.getByteInCount(), tagsFormatter.getTags());
    }

    private void emitYFlowYPointMeterPoints(TagsFormatter tagsFormatter) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("yFlow.meter.yPoint."), timestamp,
                statsEntry.getPacketsInCount(), statsEntry.getByteInCount(), tagsFormatter.getTags());
    }

    private TagsFormatter initTags() {
        TagsFormatter tags = new TagsFormatter();
        tags.addSwitchIdTag(switchId);
        tags.addMeterIdTag(statsEntry.getMeterId());
        return tags;
    }
}
