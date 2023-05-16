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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

import static org.openkilda.rulemanager.action.ActionType.PUSH_VXLAN_NOVIFLOW;

import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SwitchFlowEntriesBuilder {
    public static final long MIN_BURST_SIZE_IN_KBITS = 1024;
    public static final double BURST_COEFFICIENT = 1.05;

    private final Flow flow;

    public SwitchFlowEntriesBuilder(@NonNull Flow flow) {
        this.flow = flow;
    }

    /**
     * Construct a list of {@link FlowDumpResponse} that corresponds to the builder's flow.
     */
    public List<FlowDumpResponse> getSwitchFlowEntries(int forwardTransitEncapId,
                                                       int reverseTransitEncapId,
                                                       Integer forwardProtectedTransitEncapId,
                                                       Integer reverseProtectedTransitEncapId) {
        List<FlowDumpResponse> flowDumpResponses = new ArrayList<>();

        boolean isVxlan = flow.getEncapsulationType() == FlowEncapsulationType.VXLAN;
        FlowPath forwardPath = flow.getForwardPath();
        FlowPath reversePath = flow.getReversePath();
        long forwardCookie = forwardPath.getCookie().getValue();
        long reverseCookie = reversePath.getCookie().getValue();
        Optional<FlowPath> protectedForwardPath = Optional.ofNullable(flow.getProtectedForwardPath());
        Optional<FlowPath> protectedReversePath = Optional.ofNullable(flow.getProtectedReversePath());
        Long protectedForwardCookie = protectedForwardPath
                .map(FlowPath::getCookie).map(Cookie::getValue).orElse(null);
        Long protectedReverseCookie = protectedReversePath
                .map(FlowPath::getCookie).map(Cookie::getValue).orElse(null);

        List<PathSegment> forwardSegments = forwardPath.getSegments();
        if (forwardSegments.isEmpty()) {
            throw new IllegalArgumentException("One-switch flows are unsupported");
        }
        PathSegment firstSegment = forwardSegments.get(0);
        flowDumpResponses.add(buildSwitchFlowEntries(flow.getSrcSwitchId(),
                getFlowEntry(forwardCookie, flow.getSrcSwitchId(), flow.getSrcPort(), flow.getSrcVlan(), null,
                        firstSegment.getSrcPort(), isVxlan ? null : forwardTransitEncapId,
                        isVxlan ? forwardTransitEncapId : null,
                        forwardPath.getMeterId().getValue()),
                getFlowEntry(reverseCookie, flow.getSrcSwitchId(), firstSegment.getSrcPort(),
                        isVxlan ? null : reverseTransitEncapId,
                        isVxlan ? reverseTransitEncapId : null,
                        flow.getSrcPort(), flow.getSrcVlan(), null, null)));
        if (protectedForwardPath.isPresent()) {
            List<PathSegment> protectedForwardSegments = protectedForwardPath.get().getSegments();
            PathSegment firstProtectedSegment = protectedForwardSegments.get(0);
            flowDumpResponses.add(buildSwitchFlowEntries(flow.getSrcSwitchId(),
                    getFlowEntry(protectedReverseCookie, flow.getSrcSwitchId(), firstProtectedSegment.getSrcPort(),
                            isVxlan ? null : reverseProtectedTransitEncapId,
                            isVxlan ? reverseProtectedTransitEncapId : null,
                            flow.getSrcPort(), flow.getSrcVlan(), null, null)));
        }

        for (int i = 0; i < forwardSegments.size() - 1; i++) {
            PathSegment nsegment = forwardSegments.get(i);
            PathSegment n1segment = forwardSegments.get(i + 1);

            flowDumpResponses.add(buildSwitchFlowEntries(nsegment.getDestSwitchId(),
                    getFlowEntry(forwardCookie, nsegment.getDestSwitchId(), nsegment.getDestPort(),
                            isVxlan ? null : forwardTransitEncapId,
                            isVxlan ? forwardTransitEncapId : null,
                            n1segment.getSrcPort(), null, null, null),
                    getFlowEntry(reverseCookie, nsegment.getDestSwitchId(), n1segment.getSrcPort(),
                            isVxlan ? null : reverseTransitEncapId,
                            isVxlan ? reverseTransitEncapId : null,
                            nsegment.getDestPort(), null, null, null)));
        }

        if (protectedForwardPath.isPresent()) {
            List<PathSegment> forwardProtectedSegments = protectedForwardPath.get().getSegments();
            for (int i = 0; i < forwardProtectedSegments.size() - 1; i++) {
                PathSegment nsegment = forwardProtectedSegments.get(i);
                PathSegment n1segment = forwardProtectedSegments.get(i + 1);

                flowDumpResponses.add(buildSwitchFlowEntries(nsegment.getDestSwitchId(),
                        getFlowEntry(protectedForwardCookie, nsegment.getDestSwitchId(), nsegment.getDestPort(),
                                isVxlan ? null : forwardProtectedTransitEncapId,
                                isVxlan ? forwardProtectedTransitEncapId : null,
                                n1segment.getSrcPort(), null, null, null),
                        getFlowEntry(protectedReverseCookie, nsegment.getDestSwitchId(), n1segment.getSrcPort(),
                                isVxlan ? null : reverseProtectedTransitEncapId,
                                isVxlan ? reverseProtectedTransitEncapId : null,
                                nsegment.getDestPort(), null, null, null)));
            }
        }

        PathSegment lastSegment = forwardSegments.get(forwardSegments.size() - 1);
        flowDumpResponses.add(buildSwitchFlowEntries(flow.getDestSwitchId(),
                getFlowEntry(forwardCookie, flow.getDestSwitchId(), lastSegment.getDestPort(),
                        isVxlan ? null : forwardTransitEncapId,
                        isVxlan ? forwardTransitEncapId : null,
                        flow.getDestPort(), flow.getDestVlan(), null, null),
                getFlowEntry(reverseCookie, flow.getDestSwitchId(), flow.getDestPort(), flow.getDestVlan(),
                        null, lastSegment.getDestPort(), isVxlan ? null : reverseTransitEncapId,
                        isVxlan ? reverseTransitEncapId : null,
                        reversePath.getMeterId().getValue())));

        if (protectedForwardPath.isPresent()) {
            List<PathSegment> forwardProtectedSegments = protectedForwardPath.get().getSegments();
            PathSegment lastProtectedSegment = forwardProtectedSegments.get(forwardProtectedSegments.size() - 1);
            flowDumpResponses.add(buildSwitchFlowEntries(flow.getDestSwitchId(),
                    getFlowEntry(protectedForwardCookie, flow.getDestSwitchId(), lastProtectedSegment.getDestPort(),
                            isVxlan ? null : forwardProtectedTransitEncapId,
                            isVxlan ? forwardProtectedTransitEncapId : null,
                            flow.getDestPort(), flow.getDestVlan(), null, null)));
        }

        return flowDumpResponses;
    }

    /**
     * Construct a list of {@link MeterDumpResponse} that corresponds to the builder's flow.
     */
    public List<MeterDumpResponse> getSwitchMeterEntries() {
        List<MeterDumpResponse> switchMeterEntries = new ArrayList<>();

        FlowPath forwardPath = flow.getForwardPath();
        switchMeterEntries.add(MeterDumpResponse.builder()
                .switchId(flow.getSrcSwitchId())
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(flow.getSrcSwitchId())
                        .meterId(forwardPath.getMeterId())
                        .rate(forwardPath.getBandwidth())

                        .burst(Meter.calculateBurstSize(forwardPath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());

        FlowPath reversePath = flow.getReversePath();
        switchMeterEntries.add(MeterDumpResponse.builder()
                .switchId(flow.getDestSwitchId())
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(flow.getDestSwitchId())
                        .meterId(reversePath.getMeterId())
                        .rate(reversePath.getBandwidth())
                        .burst(Meter.calculateBurstSize(reversePath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());

        FlowPath protectedForwardPath = flow.getProtectedForwardPath();
        if (protectedForwardPath != null) {
            switchMeterEntries.add(MeterDumpResponse.builder()
                    .switchId(flow.getSrcSwitchId())
                    .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                            .switchId(flow.getSrcSwitchId())
                            .meterId(protectedForwardPath.getMeterId())
                            .rate(protectedForwardPath.getBandwidth())
                            .burst(Meter.calculateBurstSize(protectedForwardPath.getBandwidth(),
                                    MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                            .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                    .collect(Collectors.toSet()))
                            .build()))
                    .build());
        }

        FlowPath protectedReversePath = flow.getProtectedReversePath();
        if (protectedReversePath != null) {
            switchMeterEntries.add(MeterDumpResponse.builder()
                    .switchId(flow.getDestSwitchId())
                    .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                            .switchId(flow.getDestSwitchId())
                            .meterId(protectedReversePath.getMeterId())
                            .rate(protectedReversePath.getBandwidth())
                            .burst(Meter.calculateBurstSize(protectedReversePath.getBandwidth(),
                                    MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                            .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                    .collect(Collectors.toSet()))
                            .build()))
                    .build());
        }

        return switchMeterEntries;
    }

    private FlowDumpResponse buildSwitchFlowEntries(SwitchId switchId, FlowSpeakerData... flowEntries) {
        return FlowDumpResponse.builder()
                .flowSpeakerData(Lists.newArrayList(flowEntries))
                .switchId(Arrays.stream(flowEntries).findFirst().map(SpeakerData::getSwitchId).orElse(null))
                .build();
    }

    /**
     * Build a flow entry for provided data.
     */
    public static FlowSpeakerData getFlowEntry(long cookie, SwitchId switchId, int srcPort, Integer inVlan,
                                               Integer inVxlan, int dstPort,
                                               Integer outVlan, Integer outVxlan, Long meterId) {

        Set<FieldMatch> fieldMatchSet = new HashSet<>();
        fieldMatchSet.add(FieldMatch.builder().field(Field.IN_PORT).value(srcPort).build());
        if (inVlan != null) {
            fieldMatchSet.add(FieldMatch.builder().field(Field.VLAN_VID).value(inVlan).build());
        }
        if (inVxlan != null) {
            fieldMatchSet.add(FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(inVxlan).build());
        }

        List<Action> actions = new ArrayList<>();
        if (outVlan != null) {
            actions.add(SetFieldAction.builder()
                    .field(Field.VLAN_VID)
                    .value(outVlan)
                    .build());
        }

        actions.add(new PortOutAction(new PortNumber(dstPort)));
        if (outVxlan != null) {
            actions.add(PushVxlanAction.builder().vni(outVxlan).type(PUSH_VXLAN_NOVIFLOW).build());
        }

        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        if (meterId != null) {
            instructions.setGoToMeter(new MeterId(meterId));
        }

        return FlowSpeakerData.builder()
                .switchId(switchId)
                .cookie(new Cookie(cookie))
                .packetCount(7)
                .byteCount(480)
                .ofVersion(OfVersion.OF_13)
                .match(fieldMatchSet)
                .instructions(instructions)
                .build();
    }

    /**
     * Construct a list of {@link GroupDumpResponse} that corresponds to the builder's flow.
     */
    public List<GroupDumpResponse> getSwitchGroupEntries() {
        List<GroupDumpResponse> switchGroupEntries = new ArrayList<>();
        switchGroupEntries.add(GroupDumpResponse.builder()
                .switchId(flow.getSrcSwitchId())
                .groupSpeakerData(Collections.emptyList()
                    /*Lists.newArrayList(GroupEntry.builder()
                    .groupId(FLOW_GROUP_ID_A)
                    .buckets(Lists.newArrayList(new GroupBucket(0, FlowApplyActions.builder()
                                    .flowOutput(String.valueOf(FLOW_GROUP_ID_A_OUT_PORT))
                                    .setFieldActions(Collections.singletonList(
                                            FlowSetFieldAction.builder()
                                                    .fieldName("vlan_vid")
                                                    .fieldValue(String.valueOf((FLOW_GROUP_ID_A_OUT_VLAN)))
                                                    .build()
                                            ))
                                    .build()),
                            new GroupBucket(0, FlowApplyActions.builder()
                                    .flowOutput(String.valueOf(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED))
                                    .build())))
                    .build())*/)
                .build());

        switchGroupEntries.add(GroupDumpResponse.builder()
                .switchId(flow.getDestSwitchId())
                .groupSpeakerData(Collections.emptyList())
                .build());

        return switchGroupEntries;
    }
}
