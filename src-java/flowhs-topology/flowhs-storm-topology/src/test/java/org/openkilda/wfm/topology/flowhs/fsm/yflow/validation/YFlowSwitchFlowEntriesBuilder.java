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

import org.openkilda.messaging.info.group.GroupDumpResponse;
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
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SwitchFlowEntriesBuilder;

import com.google.common.collect.Sets;
import lombok.NonNull;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
     * Construct a set of {@link FlowSpeakerData} that corresponds to the builder's y-flow.
     */
    public Map<SwitchId, Collection<FlowSpeakerData>> getFlowEntries() {
        MultiValuedMap<SwitchId, FlowSpeakerData> flowEntries = new ArrayListValuedHashMap<>();
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
                    .stream().flatMap(e -> e.getFlowSpeakerData().stream())
                    .forEach(flowSpeakerData -> flowEntries.put(flowSpeakerData.getSwitchId(), flowSpeakerData));


            FlowPath forwardPath = flow.getForwardPath();
            PathSegment firstSegment = forwardPath.getSegments().get(0);
            FlowSegmentCookie forwardCookie = forwardPath.getCookie().toBuilder().yFlow(true).build();
            boolean isVxlan = flow.getEncapsulationType() == VXLAN;
            flowEntries.put(yFlow.getSharedEndpoint().getSwitchId(),
                    SwitchFlowEntriesBuilder.getFlowEntry(forwardCookie.getValue(),
                            yFlow.getSharedEndpoint().getSwitchId(),
                            flow.getSrcPort(), flow.getSrcVlan(), null,
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
                                builder.getFlowEntry(reverseCookie.getValue(), yFlow.getYPoint(),
                                        nsegment.getDestPort(),
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
                                builder.getFlowEntry(reverseCookie.getValue(), yFlow.getProtectedPathYPoint(),
                                        nsegment.getDestPort(),
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
     * Construct a set of {@link MeterSpeakerData} that corresponds to the builder's y-flow.
     */
    public Map<SwitchId, Collection<MeterSpeakerData>> getMeterEntries() {
        MultiValuedMap<SwitchId, MeterSpeakerData> meterEntries = new ArrayListValuedHashMap<>();
        yFlow.getSubFlows().forEach(subFlow -> {
            Flow flow = subFlow.getFlow();
            SwitchFlowEntriesBuilder builder = new SwitchFlowEntriesBuilder(flow);
            builder.getSwitchMeterEntries().stream().flatMap(e -> e.getMeterSpeakerData().stream())
                    .forEach(meterSpeakerData -> meterEntries.put(meterSpeakerData.getSwitchId(), meterSpeakerData));

            FlowPath forwardPath = flow.getForwardPath();
            meterEntries.put(yFlow.getSharedEndpoint().getSwitchId(), MeterSpeakerData.builder()
                    .switchId(yFlow.getSharedEndpoint().getSwitchId())
                    .meterId(yFlow.getSharedEndpointMeterId())
                    .rate(forwardPath.getBandwidth())
                    .burst(Meter.calculateBurstSize(forwardPath.getBandwidth(),
                            MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                    .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                            .collect(Collectors.toSet()))
                    .build());

            if (!yFlow.getYPoint().equals(flow.getSrcSwitchId())) {
                FlowPath reversePath = flow.getReversePath();
                meterEntries.put(yFlow.getYPoint(), MeterSpeakerData.builder()
                        .switchId(yFlow.getYPoint())
                        .meterId(yFlow.getMeterId())
                        .rate(reversePath.getBandwidth())
                        .burst(Meter.calculateBurstSize(reversePath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build());
            }

            if (yFlow.isAllocateProtectedPath() && yFlow.getProtectedPathYPoint() != null
                    && !yFlow.getProtectedPathYPoint().equals(flow.getSrcSwitchId())) {
                FlowPath reversePath = flow.getProtectedReversePath();
                meterEntries.put(yFlow.getProtectedPathYPoint(), MeterSpeakerData.builder()
                        .switchId(yFlow.getProtectedPathYPoint())
                        .meterId(yFlow.getProtectedPathMeterId())
                        .rate(reversePath.getBandwidth())
                        .burst(Meter.calculateBurstSize(reversePath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build());
            }
        });
        return meterEntries.asMap();
    }

    /**
     * Construct a set of {@link GroupSpeakerData} that corresponds to the builder's y-flow.
     */
    public Map<SwitchId, Collection<GroupSpeakerData>> getGroupEntries() {
        MultiValuedMap<SwitchId, GroupSpeakerData> groupEntries = new ArrayListValuedHashMap<>();
        yFlow.getSubFlows().forEach(subFlow -> {
            Flow flow = subFlow.getFlow();
            SwitchFlowEntriesBuilder builder = new SwitchFlowEntriesBuilder(flow);
            builder.getSwitchGroupEntries().stream().map(GroupDumpResponse::getGroupSpeakerData)
                    .flatMap(Collection::stream)
                    .filter(Objects::nonNull)
                    .forEach(e -> groupEntries.put(e.getSwitchId(), e));
        });
        return groupEntries.asMap();
    }
}
