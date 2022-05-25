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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.validation;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.SwitchFlowEntriesBuilder.BURST_COEFFICIENT;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.SwitchFlowEntriesBuilder.MIN_BURST_SIZE_IN_KBITS;

import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.model.YFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SwitchFlowEntriesBuilder;

import lombok.NonNull;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class YFlowSwitchFlowEntriesBuilder {
    private final YFlow yFlow;
    private final TransitVlanRepository transitVlanRepository;
    private final VxlanRepository vxlanRepository;

    public YFlowSwitchFlowEntriesBuilder(@NonNull YFlow yFlow,
                                         @NonNull TransitVlanRepository transitVlanRepository,
                                         @NonNull VxlanRepository vxlanRepository) {
        this.yFlow = yFlow;
        this.transitVlanRepository = transitVlanRepository;
        this.vxlanRepository = vxlanRepository;

        checkNotNull(yFlow.getMeterId(), "MeterId is required for y-flow");
        checkNotNull(yFlow.getSharedEndpointMeterId(), "SharedEndpointMeterId is required for y-flow");
        checkNotNull(yFlow.getYPoint(), "Y-point is required for y-flow");

        yFlow.getSubFlows().forEach(subFlow -> {
            Flow flow = subFlow.getFlow();
            if (!flow.getSrcSwitchId().equals(yFlow.getSharedEndpoint().getSwitchId())) {
                throw new IllegalArgumentException("Invalid sub-flow source - "
                        + "doesn't correspond to y-flow shared endpoint");
            }
        });
    }

    /**
     * Construct a set of {@link FlowEntry} that corresponds to the builder's y-flow.
     */
    public Map<SwitchId, Collection<FlowEntry>> getFlowEntries() {
        MultiValuedMap<SwitchId, FlowEntry> flowEntries = new ArrayListValuedHashMap<>();
        yFlow.getSubFlows().forEach(subFlow -> {
            Flow flow = subFlow.getFlow();
            SwitchFlowEntriesBuilder builder = new SwitchFlowEntriesBuilder(flow);
            int forwardTransitEncId = getEncapsulationId(flow.getEncapsulationType(), flow.getForwardPathId())
                    .orElseThrow(IllegalStateException::new);
            int reverseTransitEncId = getEncapsulationId(flow.getEncapsulationType(), flow.getReversePathId())
                    .orElseThrow(IllegalStateException::new);
            Integer protectedForwardTransitEncId = getEncapsulationId(flow.getEncapsulationType(),
                    flow.getProtectedForwardPathId()).orElse(null);
            Integer protectedReverseTransitEncId = getEncapsulationId(flow.getEncapsulationType(),
                    flow.getProtectedReversePathId()).orElse(null);

            builder.getSwitchFlowEntries(forwardTransitEncId, reverseTransitEncId,
                            protectedForwardTransitEncId, protectedReverseTransitEncId)
                    .forEach(entries -> flowEntries.putAll(entries.getSwitchId(), entries.getFlowEntries()));

            FlowPath forwardPath = flow.getForwardPath();
            PathSegment firstSegment = forwardPath.getSegments().get(0);
            FlowSegmentCookie forwardCookie = forwardPath.getCookie().toBuilder().yFlow(true).build();
            boolean isVxlan = flow.getEncapsulationType() == VXLAN;
            flowEntries.put(yFlow.getSharedEndpoint().getSwitchId(),
                    builder.getFlowEntry(forwardCookie.getValue(), flow.getSrcPort(), flow.getSrcVlan(), null,
                            firstSegment.getSrcPort(), isVxlan ? null : forwardTransitEncId,
                            isVxlan ? forwardTransitEncId : null,
                            yFlow.getSharedEndpointMeterId().getValue()));

            if (!yFlow.getYPoint().equals(flow.getSrcSwitchId())) {
                FlowPath reversePath = flow.getReversePath();
                FlowSegmentCookie reverseCookie = reversePath.getCookie().toBuilder().yFlow(true).build();
                List<PathSegment> reverseSegments = reversePath.getSegments();
                for (int i = 0; i < reverseSegments.size() - 1; i++) {
                    PathSegment nsegment = reverseSegments.get(i);
                    PathSegment n1segment = reverseSegments.get(i + 1);

                    if (nsegment.getDestSwitchId().equals(yFlow.getYPoint())) {
                        flowEntries.put(yFlow.getYPoint(),
                                builder.getFlowEntry(reverseCookie.getValue(), nsegment.getDestPort(),
                                        isVxlan ? null : reverseTransitEncId,
                                        isVxlan ? reverseTransitEncId : null,
                                        n1segment.getSrcPort(), null, null,
                                        yFlow.getMeterId().getValue()));
                    }
                }
            }

            if (yFlow.isAllocateProtectedPath() && yFlow.getProtectedPathYPoint() != null
                    && !yFlow.getProtectedPathYPoint().equals(flow.getSrcSwitchId())) {
                FlowPath reversePath = flow.getProtectedReversePath();
                FlowSegmentCookie reverseCookie = reversePath.getCookie().toBuilder().yFlow(true).build();
                List<PathSegment> reverseSegments = reversePath.getSegments();
                for (int i = 0; i < reverseSegments.size() - 1; i++) {
                    PathSegment nsegment = reverseSegments.get(i);
                    PathSegment n1segment = reverseSegments.get(i + 1);

                    if (nsegment.getDestSwitchId().equals(yFlow.getProtectedPathYPoint())) {
                        flowEntries.put(yFlow.getProtectedPathYPoint(),
                                builder.getFlowEntry(reverseCookie.getValue(), nsegment.getDestPort(),
                                        isVxlan ? null : protectedReverseTransitEncId,
                                        isVxlan ? protectedReverseTransitEncId : null,
                                        n1segment.getSrcPort(), null, null,
                                        yFlow.getProtectedPathMeterId().getValue()));
                    }
                }
            }
        });
        return flowEntries.asMap();
    }

    private Optional<Integer> getEncapsulationId(FlowEncapsulationType encapsulationType, PathId pathId) {
        if (encapsulationType == TRANSIT_VLAN) {
            return transitVlanRepository.findByPathId(pathId).map(TransitVlan::getVlan);
        } else if (encapsulationType == VXLAN) {
            return vxlanRepository.findByPathId(pathId, pathId).stream().findFirst().map(Vxlan::getVni);
        }
        throw new IllegalStateException("Unsupported encapsulationType " + encapsulationType);
    }

    /**
     * Construct a set of {@link MeterEntry} that corresponds to the builder's y-flow.
     */
    public Map<SwitchId, Collection<MeterEntry>> getMeterEntries() {
        MultiValuedMap<SwitchId, MeterEntry> meterEntries = new ArrayListValuedHashMap<>();
        yFlow.getSubFlows().forEach(subFlow -> {
            Flow flow = subFlow.getFlow();
            SwitchFlowEntriesBuilder builder = new SwitchFlowEntriesBuilder(flow);
            builder.getSwitchMeterEntries()
                    .forEach(entries -> meterEntries.putAll(entries.getSwitchId(), entries.getMeterEntries()));

            FlowPath forwardPath = flow.getForwardPath();
            meterEntries.put(yFlow.getSharedEndpoint().getSwitchId(), MeterEntry.builder()
                    .meterId(yFlow.getSharedEndpointMeterId().getValue())
                    .rate(forwardPath.getBandwidth())
                    .burstSize(Meter.calculateBurstSize(forwardPath.getBandwidth(),
                            MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                    .flags(Meter.getMeterKbpsFlags())
                    .build());

            if (!yFlow.getYPoint().equals(flow.getSrcSwitchId())) {
                FlowPath reversePath = flow.getReversePath();
                meterEntries.put(yFlow.getYPoint(), MeterEntry.builder()
                        .meterId(yFlow.getMeterId().getValue())
                        .rate(reversePath.getBandwidth())
                        .burstSize(Meter.calculateBurstSize(reversePath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterKbpsFlags())
                        .build());
            }

            if (yFlow.isAllocateProtectedPath() && yFlow.getProtectedPathYPoint() != null
                    && !yFlow.getProtectedPathYPoint().equals(flow.getSrcSwitchId())) {
                FlowPath reversePath = flow.getProtectedReversePath();
                meterEntries.put(yFlow.getProtectedPathYPoint(), MeterEntry.builder()
                        .meterId(yFlow.getProtectedPathMeterId().getValue())
                        .rate(reversePath.getBandwidth())
                        .burstSize(Meter.calculateBurstSize(reversePath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterKbpsFlags())
                        .build());
            }
        });
        return meterEntries.asMap();
    }

    /**
     * Construct a set of {@link GroupEntry} that corresponds to the builder's y-flow.
     */
    public Map<SwitchId, Collection<GroupEntry>> getGroupEntries() {
        MultiValuedMap<SwitchId, GroupEntry> groupEntries = new ArrayListValuedHashMap<>();
        yFlow.getSubFlows().forEach(subFlow -> {
            Flow flow = subFlow.getFlow();
            SwitchFlowEntriesBuilder builder = new SwitchFlowEntriesBuilder(flow);
            builder.getSwitchGroupEntries()
                    .forEach(entries -> groupEntries.putAll(entries.getSwitchId(), entries.getGroupEntries()));
        });
        return groupEntries.asMap();
    }
}
