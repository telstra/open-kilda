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

import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.EGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.ONE_SWITCH;

import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper.Direction;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyMeterDescriptor;
import org.openkilda.wfm.topology.stats.model.EndpointFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.shade.org.apache.curator.shaded.com.google.common.collect.Sets;

import java.util.Set;

@Slf4j
public final class FlowEndpointStatsEntryHandler extends BaseFlowStatsEntryHandler {
    private static final Set<MeasurePoint> INGRESS_SUBFLOW_MEASURE_POINTS_TO_IGNORE = Sets.newHashSet(
            INGRESS, ONE_SWITCH);
    private static final Set<MeasurePoint> REVERSE_SUBFLOW_PATH_MEASURE_POINTS_TO_IGNORE = Sets.newHashSet(
            INGRESS, ONE_SWITCH, EGRESS);

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
    public void handleStatsEntry(EndpointFlowDescriptor descriptor) {
        TagsFormatter tags = initTags(false);
        tags.addFlowIdTag(descriptor.getFlowId());
        emitMeterPoints(tags, descriptor.getMeasurePoint(), descriptor.isHasMirror());
    }

    @Override
    public void handleStatsEntry(CommonFlowDescriptor descriptor) {
        // nothing to do here
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
        if (shouldSkipStats(descriptor)) {
            return; // we must ignore sub flow stats if there are y flow rules on same switch
        }
        TagsFormatter tags = initTags(true);
        tags.addFlowIdTag(descriptor.getSubFlowId());
        tags.addYFlowIdTag(descriptor.getYFlowId());
        emitMeterPoints(tags, descriptor.getMeasurePoint(), false);
    }

    @Override
    public void handleStatsEntry(DummyFlowDescriptor descriptor) {
        Cookie cookie = new Cookie(statsEntry.getCookie());
        if (cookie.getType() == SERVICE_OR_FLOW_SEGMENT) {
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

    private void emitMeterPoints(TagsFormatter tags, MeasurePoint measurePoint, boolean hasMirror) {
        FlowSegmentCookie cookie = decodeFlowSegmentCookie(statsEntry.getCookie());
        if (cookie != null && cookie.getType() == SERVICE_OR_FLOW_SEGMENT
                && cookie.isMirror() == hasMirror) {
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

    private boolean shouldSkipStats(YFlowSubDescriptor descriptor) {
        FlowSegmentCookie cookie = new FlowSegmentCookie(statsEntry.getCookie());
        return cookie.getType() == SERVICE_OR_FLOW_SEGMENT && !cookie.isYFlow()
                && (isIngressStartsInSharedPoint(descriptor, cookie) || isEgressEndsInYPoint(descriptor, cookie));
    }

    private static boolean isIngressStartsInSharedPoint(YFlowSubDescriptor descriptor, FlowSegmentCookie cookie) {
        return FlowPathDirection.FORWARD.equals(cookie.getDirection())
                && INGRESS_SUBFLOW_MEASURE_POINTS_TO_IGNORE.contains(descriptor.getMeasurePoint());
    }

    private boolean isEgressEndsInYPoint(YFlowSubDescriptor descriptor, FlowSegmentCookie cookie) {
        return FlowPathDirection.REVERSE.equals(cookie.getDirection())
                && REVERSE_SUBFLOW_PATH_MEASURE_POINTS_TO_IGNORE.contains(descriptor.getMeasurePoint())
                && switchId.equals(descriptor.getYPointSwitchId());
    }
}
