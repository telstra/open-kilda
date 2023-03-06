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
import org.openkilda.wfm.topology.stats.model.EndpointFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AnyFlowStatsEntryHandler extends BaseFlowStatsEntryHandler {
    public static void apply(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, FlowStatsEntry statsEntry,
            KildaEntryDescriptor descriptor) {
        AnyFlowStatsEntryHandler handler = new AnyFlowStatsEntryHandler(meterEmitter, switchId, timestamp, statsEntry);
        handler.handle(descriptor);
    }

    private AnyFlowStatsEntryHandler(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, long timestamp, FlowStatsEntry statsEntry) {
        super(meterEmitter, switchId, statsEntry, timestamp);
    }

    @Override
    public void handleStatsEntry(CommonFlowDescriptor descriptor) {
        TagsFormatter tags = initTags();
        tags.addFlowIdTag(descriptor.getFlowId());
        tags.addDirectionTag(descriptor.getCookie().getDirection());
        emitMeterPoints(tags);
    }

    @Override
    public void handleStatsEntry(YFlowDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(YFlowSubDescriptor descriptor) {
        TagsFormatter tags = initTags();
        tags.addFlowIdTag(descriptor.getSubFlowId());
        tags.addDirectionTag(descriptor.getCookie().getDirection());
        emitMeterPoints(tags);
    }

    @Override
    public void handleStatsEntry(DummyFlowDescriptor descriptor) {
        FlowSegmentCookie cookie = decodeFlowSegmentCookie(statsEntry.getCookie());
        TagsFormatter tags = initTags(cookie);
        directionFromCookieIntoTags(cookie, tags);
        emitMeterPoints(tags);
    }

    @Override
    public void handleStatsEntry(DummyMeterDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedDescriptorMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(StatVlanDescriptor descriptor) {
        // nothing to do here
    }

    @Override
    public void handleStatsEntry(EndpointFlowDescriptor descriptor) {
        handleStatsEntry((CommonFlowDescriptor) descriptor);
    }

    private void emitMeterPoints(TagsFormatter tagsFormatter) {
        meterEmitter.emitPacketAndBytePoints(
                new MetricFormatter("flow.raw."), timestamp, statsEntry.getPacketCount(), statsEntry.getByteCount(),
                tagsFormatter.getTags());
    }

    private TagsFormatter initTags() {
        return initTags(decodeFlowSegmentCookie(statsEntry.getCookie()));
    }

    private TagsFormatter initTags(FlowSegmentCookie decodedCookie) {
        TagsFormatter tags = new TagsFormatter();
        tags.addFlowIdTag(null);
        tags.addDirectionTag(Direction.UNKNOWN);

        tags.addSwitchIdTag(switchId);
        tags.addCookieTag(statsEntry.getCookie());
        tags.addTableIdTag(statsEntry.getTableId());
        tags.addInPortTag(statsEntry.getInPort());
        tags.addOutPortTag(statsEntry.getOutPort());

        CookieType cookieType;
        boolean flowSatellite = isFlowSatelliteEntry(decodedCookie);
        if (decodedCookie != null) {
            cookieType = decodedCookie.getType();
            if (flowSatellite) {
                tags.addIsFlowRttInjectTag(cookieType == CookieType.SERVER_42_FLOW_RTT_INGRESS);
                tags.addIsMirrorTag(decodedCookie.isMirror());
                tags.addIsLoopTag(decodedCookie.isLooped());
            }
        } else {
            cookieType = new Cookie(statsEntry.getCookie()).getType();
        }
        tags.addIsFlowSatelliteTag(flowSatellite);
        tags.addCookieTypeTag(cookieType);

        return tags;
    }
}
