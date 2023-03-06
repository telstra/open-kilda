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

import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import com.google.common.collect.Lists;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class SwitchFlowEntriesBuilder {
    public static final long MIN_BURST_SIZE_IN_KBITS = 1024;
    public static final double BURST_COEFFICIENT = 1.05;

    private final Flow flow;

    public SwitchFlowEntriesBuilder(@NonNull Flow flow) {
        this.flow = flow;
    }

    /**
     * Construct a list of {@link SwitchFlowEntries} that corresponds to the builder's flow.
     */
    public List<SwitchFlowEntries> getSwitchFlowEntries(int forwardTransitEncapId,
                                                        int reverseTransitEncapId,
                                                        Integer forwardProtectedTransitEncapId,
                                                        Integer reverseProtectedTransitEncapId) {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

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
        switchEntries.add(buildSwitchFlowEntries(flow.getSrcSwitchId(),
                getFlowEntry(forwardCookie, flow.getSrcPort(), flow.getSrcVlan(), null,
                        firstSegment.getSrcPort(), isVxlan ? null : forwardTransitEncapId,
                        isVxlan ? forwardTransitEncapId : null,
                        forwardPath.getMeterId().getValue()),
                getFlowEntry(reverseCookie, firstSegment.getSrcPort(), isVxlan ? null : reverseTransitEncapId,
                        isVxlan ? reverseTransitEncapId : null,
                        flow.getSrcPort(), flow.getSrcVlan(), null, null)));
        if (protectedForwardPath.isPresent()) {
            List<PathSegment> protectedForwardSegments = protectedForwardPath.get().getSegments();
            PathSegment firstProtectedSegment = protectedForwardSegments.get(0);
            switchEntries.add(buildSwitchFlowEntries(flow.getSrcSwitchId(),
                    getFlowEntry(protectedReverseCookie, firstProtectedSegment.getSrcPort(),
                            isVxlan ? null : reverseProtectedTransitEncapId,
                            isVxlan ? reverseProtectedTransitEncapId : null,
                            flow.getSrcPort(), flow.getSrcVlan(), null, null)));
        }

        for (int i = 0; i < forwardSegments.size() - 1; i++) {
            PathSegment nsegment = forwardSegments.get(i);
            PathSegment n1segment = forwardSegments.get(i + 1);

            switchEntries.add(buildSwitchFlowEntries(nsegment.getDestSwitchId(),
                    getFlowEntry(forwardCookie, nsegment.getDestPort(), isVxlan ? null : forwardTransitEncapId,
                            isVxlan ? forwardTransitEncapId : null,
                            n1segment.getSrcPort(), null, null, null),
                    getFlowEntry(reverseCookie, n1segment.getSrcPort(), isVxlan ? null : reverseTransitEncapId,
                            isVxlan ? reverseTransitEncapId : null,
                            nsegment.getDestPort(), null, null, null)));
        }

        if (protectedForwardPath.isPresent()) {
            List<PathSegment> forwardProtectedSegments = protectedForwardPath.get().getSegments();
            for (int i = 0; i < forwardProtectedSegments.size() - 1; i++) {
                PathSegment nsegment = forwardProtectedSegments.get(i);
                PathSegment n1segment = forwardProtectedSegments.get(i + 1);

                switchEntries.add(buildSwitchFlowEntries(nsegment.getDestSwitchId(),
                        getFlowEntry(protectedForwardCookie, nsegment.getDestPort(),
                                isVxlan ? null : forwardProtectedTransitEncapId,
                                isVxlan ? forwardProtectedTransitEncapId : null,
                                n1segment.getSrcPort(), null, null, null),
                        getFlowEntry(protectedReverseCookie, n1segment.getSrcPort(),
                                isVxlan ? null : reverseProtectedTransitEncapId,
                                isVxlan ? reverseProtectedTransitEncapId : null,
                                nsegment.getDestPort(), null, null, null)));
            }
        }

        PathSegment lastSegment = forwardSegments.get(forwardSegments.size() - 1);
        switchEntries.add(buildSwitchFlowEntries(flow.getDestSwitchId(),
                getFlowEntry(forwardCookie, lastSegment.getDestPort(), isVxlan ? null : forwardTransitEncapId,
                        isVxlan ? forwardTransitEncapId : null,
                        flow.getDestPort(), flow.getDestVlan(), null, null),
                getFlowEntry(reverseCookie, flow.getDestPort(), flow.getDestVlan(), null,
                        lastSegment.getDestPort(), isVxlan ? null : reverseTransitEncapId,
                        isVxlan ? reverseTransitEncapId : null,
                        reversePath.getMeterId().getValue())));

        if (protectedForwardPath.isPresent()) {
            List<PathSegment> forwardProtectedSegments = protectedForwardPath.get().getSegments();
            PathSegment lastProtectedSegment = forwardProtectedSegments.get(forwardProtectedSegments.size() - 1);
            switchEntries.add(buildSwitchFlowEntries(flow.getDestSwitchId(),
                    getFlowEntry(protectedForwardCookie, lastProtectedSegment.getDestPort(),
                            isVxlan ? null : forwardProtectedTransitEncapId,
                            isVxlan ? forwardProtectedTransitEncapId : null,
                            flow.getDestPort(), flow.getDestVlan(), null, null)));
        }

        return switchEntries;
    }

    /**
     * Construct a list of {@link SwitchMeterEntries} that corresponds to the builder's flow.
     */
    public List<SwitchMeterEntries> getSwitchMeterEntries() {
        List<SwitchMeterEntries> switchMeterEntries = new ArrayList<>();

        FlowPath forwardPath = flow.getForwardPath();
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(flow.getSrcSwitchId())
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(forwardPath.getMeterId().getValue())
                        .rate(forwardPath.getBandwidth())
                        .burstSize(Meter.calculateBurstSize(forwardPath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterKbpsFlags())
                        .build()))
                .build());

        FlowPath reversePath = flow.getReversePath();
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(flow.getDestSwitchId())
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(reversePath.getMeterId().getValue())
                        .rate(reversePath.getBandwidth())
                        .burstSize(Meter.calculateBurstSize(reversePath.getBandwidth(),
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterKbpsFlags())
                        .build()))
                .build());

        FlowPath protectedForwardPath = flow.getProtectedForwardPath();
        if (protectedForwardPath != null) {
            switchMeterEntries.add(SwitchMeterEntries.builder()
                    .switchId(flow.getSrcSwitchId())
                    .meterEntries(Collections.singletonList(MeterEntry.builder()
                            .meterId(protectedForwardPath.getMeterId().getValue())
                            .rate(protectedForwardPath.getBandwidth())
                            .burstSize(Meter.calculateBurstSize(protectedForwardPath.getBandwidth(),
                                    MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                            .flags(Meter.getMeterKbpsFlags())
                            .build()))
                    .build());
        }

        FlowPath protectedReversePath = flow.getProtectedReversePath();
        if (protectedReversePath != null) {
            switchMeterEntries.add(SwitchMeterEntries.builder()
                    .switchId(flow.getDestSwitchId())
                    .meterEntries(Collections.singletonList(MeterEntry.builder()
                            .meterId(protectedReversePath.getMeterId().getValue())
                            .rate(protectedReversePath.getBandwidth())
                            .burstSize(Meter.calculateBurstSize(protectedReversePath.getBandwidth(),
                                    MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                            .flags(Meter.getMeterKbpsFlags())
                            .build()))
                    .build());
        }

        return switchMeterEntries;
    }

    private SwitchFlowEntries buildSwitchFlowEntries(SwitchId switchId, FlowEntry... flowEntries) {
        return SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(Lists.newArrayList(flowEntries))
                .build();
    }

    /**
     * Build a flow entry for provided data.
     */
    public static FlowEntry getFlowEntry(long cookie, int srcPort, Integer inVlan, Integer inVxlan, int dstPort,
                                         Integer outVlan, Integer outVxlan, Long meterId) {
        Collection<FlowSetFieldAction> flowSetFieldActions = outVlan != null
                ? Lists.newArrayList(FlowSetFieldAction.builder().fieldName("vlan_vid")
                .fieldValue(String.valueOf(outVlan)).build()) : Lists.newArrayList();

        return FlowEntry.builder()
                .cookie(cookie)
                .packetCount(7)
                .byteCount(480)
                .version("OF_13")
                .match(FlowMatchField.builder()
                        .inPort(String.valueOf(srcPort))
                        .vlanVid(inVlan != null ? String.valueOf(inVlan) : null)
                        .tunnelId(inVxlan != null ? String.valueOf(inVxlan) : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(String.valueOf(dstPort))
                                .setFieldActions(flowSetFieldActions)
                                .pushVxlan(outVxlan != null ? String.valueOf(outVxlan) : null)
                                .build())
                        .goToMeter(meterId)
                        .build())
                .build();
    }

    /**
     * Construct a list of {@link SwitchGroupEntries} that corresponds to the builder's flow.
     */
    public List<SwitchGroupEntries> getSwitchGroupEntries() {
        List<SwitchGroupEntries> switchGroupEntries = new ArrayList<>();
        switchGroupEntries.add(SwitchGroupEntries.builder()
                .switchId(flow.getSrcSwitchId())
                .groupEntries(Collections.emptyList()
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

        switchGroupEntries.add(SwitchGroupEntries.builder()
                .switchId(flow.getDestSwitchId())
                .groupEntries(Collections.emptyList())
                .build());

        return switchGroupEntries;
    }
}
