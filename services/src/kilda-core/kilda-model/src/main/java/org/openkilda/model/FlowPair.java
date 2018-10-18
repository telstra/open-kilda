/* Copyright 2018 Telstra Open Source
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

package org.openkilda.model;

import com.google.common.annotations.VisibleForTesting;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * A pair (forward & reverse) of unidirectional flows.
 *
 * @deprecated Must be replaced with new model entities: {@link org.openkilda.model.Flow}
 */
@Deprecated
public class FlowPair implements Serializable {
    private static final long serialVersionUID = 1L;

    public final UnidirectionalFlow forward;
    public final UnidirectionalFlow reverse;

    /**
     * Build a flow pair using the provided flow for the forward and symmetric copy for the reverse.
     */
    @VisibleForTesting
    public static FlowPair buildPair(UnidirectionalFlow flow) {
        return new FlowPair(new UnidirectionalFlow(flow.getFlowEntity(), flow.getFlowPath(),
                flow.getTransitVlanEntity(), true),
                new UnidirectionalFlow(flow.getFlowEntity(), flow.getFlowPath().toBuilder().build(),
                        flow.getTransitVlanEntity().toBuilder().build(), false));
    }

    private FlowPair(UnidirectionalFlow forward, UnidirectionalFlow reverse) {
        this.forward = forward;
        this.reverse = reverse;
    }

    @VisibleForTesting
    public FlowPair(Switch srcSwitch, Switch destSwitch) {
        this(UUID.randomUUID().toString(), srcSwitch, 0, 0, destSwitch, 0, 0, 0);
    }

    @VisibleForTesting
    public FlowPair(String flowId, Switch srcSwitch, int srcPort, int srcVlan,
                    Switch destSwitch, int destPort, int destVlan, long unmaskedCookie) {
        FlowPath forwardPath = buildFlowPath(flowId, srcSwitch, destSwitch, Cookie.buildForwardCookie(unmaskedCookie));
        FlowPath reversePath = buildFlowPath(flowId, destSwitch, srcSwitch, Cookie.buildReverseCookie(unmaskedCookie));

        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .destVlan(destVlan)
                .forwardPath(forwardPath)
                .reversePath(reversePath)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        TransitVlan forwardTransitVlan = TransitVlan.builder()
                .flowId(flowId)
                .pathId(forwardPath.getPathId())
                .vlan(srcVlan + destVlan + 1)
                .build();

        TransitVlan reverseTransitVlan = TransitVlan.builder()
                .flowId(flowId)
                .pathId(reversePath.getPathId())
                .vlan(srcVlan + destVlan + 2)
                .build();

        forward = new UnidirectionalFlow(flow, forwardPath, forwardTransitVlan, true);
        reverse = new UnidirectionalFlow(flow, reversePath, reverseTransitVlan, false);
    }

    public FlowPair(Flow flow, TransitVlan forwardTransitVlan, TransitVlan reverseTransitVlan) {
        forward = new UnidirectionalFlow(flow, flow.getForwardPath(), forwardTransitVlan, true);
        reverse = new UnidirectionalFlow(flow, flow.getReversePath(), reverseTransitVlan, false);
    }

    public UnidirectionalFlow getForward() {
        return forward;
    }

    public UnidirectionalFlow getReverse() {
        return reverse;
    }

    public void setStatus(FlowStatus status) {
        forward.setStatus(status);
        reverse.setStatus(status);
    }

    public Flow getFlowEntity() {
        return forward.getFlowEntity();
    }

    public TransitVlan getForwardTransitVlanEntity() {
        return forward.getTransitVlanEntity();
    }

    public TransitVlan getReverseTransitVlanEntity() {
        return reverse.getTransitVlanEntity();
    }

    public boolean isActive() {
        return forward.isActive() && reverse.isActive();
    }

    public void setTimeCreate(Instant timeCreate) {
        forward.setTimeCreate(timeCreate);
        reverse.setTimeCreate(timeCreate);
    }

    public void setTimeModify(Instant timeModify) {
        forward.setTimeModify(timeModify);
        reverse.setTimeModify(timeModify);
    }

    private FlowPath buildFlowPath(String flowId, Switch pathSrc, Switch pathDest, Cookie cookie) {
        return FlowPath.builder()
                .flowId(flowId)
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(pathSrc)
                .destSwitch(pathDest)
                .segments(Collections.emptyList())
                .cookie(cookie)
                .build();
    }
}
