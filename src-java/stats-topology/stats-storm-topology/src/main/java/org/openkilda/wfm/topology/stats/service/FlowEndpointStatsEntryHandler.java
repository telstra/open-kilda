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

import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper.Direction;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyMeterDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class FlowEndpointStatsEntryHandler extends BaseFlowStatsEntryHandler {
    /**
     * Handle stats entry.
     */
    public static void apply(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, FlowStatsEntry statsEntry,
            KildaEntryDescriptor descriptor) {
        FlowEndpointStatsEntryHandler handler = new FlowEndpointStatsEntryHandler(
                meterEmitter, switchId, timestamp, statsEntry);
        handler.handle(descriptor);
    }

    private FlowEndpointStatsEntryHandler(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, FlowStatsEntry statsEntry) {
        super(meterEmitter, switchId, statsEntry, timestamp);
    }

    @Override
    public void handleStatsEntry(CommonFlowDescriptor descriptor) {
        TagsFormatter tags = initTags(false);
        tags.addFlowIdTag(descriptor.getFlowId());
        emitMeterPoints(tags, descriptor.getMeasurePoint());
    }

    @Override
    public void handleStatsEntry(YFlowDescriptor descriptor) {
        TagsFormatter tags = initTags(false);
        tags.addYFlowIdTag(descriptor.getYFlowId());

        switch (descriptor.getMeasurePoint()) {
            case Y_FLOW_SHARED:
                emitYFlowSharedPoints(tags);
                break;
            case Y_FLOW_Y_POINT:
                emitYFlowYPointPoints(tags);
                break;
            default:
                // nothing to do here
        }
    }

    @Override
    public void handleStatsEntry(YFlowSubDescriptor descriptor) {
        TagsFormatter tags = initTags(true);
        tags.addFlowIdTag(descriptor.getSubFlowId());
        tags.addYFlowIdTag(descriptor.getYFlowId());
        emitMeterPoints(tags, descriptor.getMeasurePoint());
    }

    @Override
    public void handleStatsEntry(DummyFlowDescriptor descriptor) {
        Cookie cookie = new Cookie(statsEntry.getCookie());
        if (cookie.getType() == CookieType.SERVICE_OR_FLOW_SEGMENT) {
            log.warn(
                    "Missed cache entry for stats record from switch {} from table {} with cookie {}",
                    switchId, statsEntry.getTableId(), cookie);
        } else {
            log.debug(
                    "Ignoring stats entry from switch {} from table {} with cookie {}",
                    switchId, statsEntry.getTableId(), cookie);
        }
    }

    @Override
    public void handleStatsEntry(DummyMeterDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(StatVlanDescriptor descriptor) {
        FlowSegmentCookie cookie = new FlowSegmentCookie(statsEntry.getCookie());
        TagsFormatter tags = new TagsFormatter();
        tags.addDirectionTag(Direction.UNKNOWN);
        directionFromCookieIntoTags(cookie, tags);
        tags.addFlowIdTag(descriptor.getFlowId());
        tags.addVlanTag(cookie.getStatsVlan());
        tags.addSwitchIdTag(switchId);
        tags.addCookieTag(cookie);
        tags.addInPortTag(statsEntry.getInPort());
        emitStatVlan(tags);
    }

    private void emitMeterPoints(TagsFormatter tags, MeasurePoint measurePoint) {
        FlowSegmentCookie cookie = decodeFlowSegmentCookie(statsEntry.getCookie());
        if (cookie != null && !isFlowSatelliteEntry(cookie)) {
            directionFromCookieIntoTags(cookie, tags);
            switch (measurePoint) {
                case INGRESS:
                    emitIngressPoints(tags);
                    break;
                case EGRESS:
                    emitEgressPoints(tags);
                    break;
                case ONE_SWITCH:
                    emitIngressPoints(tags);
                    emitEgressPoints(tags);
                    break;
                default:
                    // nothing to do here
            }
        }
    }

    private void emitIngressPoints(TagsFormatter tags) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("flow.ingress."), timestamp,
                statsEntry.getPacketCount(), statsEntry.getByteCount(), tags.getTags());
    }

    private void emitEgressPoints(TagsFormatter tags) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("flow."), timestamp,
                statsEntry.getPacketCount(), statsEntry.getByteCount(), tags.getTags());
    }

    private void emitYFlowSharedPoints(TagsFormatter tags) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("yFlow.shared."), timestamp,
                statsEntry.getPacketCount(), statsEntry.getByteCount(), tags.getTags());
    }

    private void emitYFlowYPointPoints(TagsFormatter tags) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("yFlow.yPoint."), timestamp,
                statsEntry.getPacketCount(), statsEntry.getByteCount(), tags.getTags());
    }

    private void emitStatVlan(TagsFormatter tags) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("flow.vlan."), timestamp,
                statsEntry.getPacketCount(), statsEntry.getByteCount(), tags.getTags());
    }

    private TagsFormatter initTags(boolean isYSubFlow) {
        TagsFormatter tags = new TagsFormatter();
        tags.addFlowIdTag(null);
        tags.addDirectionTag(Direction.UNKNOWN);
        tags.addIsYFlowSubFlowTag(isYSubFlow);
        return tags;
    }
}
