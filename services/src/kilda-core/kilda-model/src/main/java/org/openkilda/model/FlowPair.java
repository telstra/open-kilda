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
        this(UUID.randomUUID().toString(), srcSwitch, 0, 0, destSwitch, 0, 0);
    }

    @VisibleForTesting
    public FlowPair(String flowId, Switch srcSwitch, int srcPort, int srcVlan,
                    Switch destSwitch, int destPort, int destVlan) {
        PathId forwardPathId = new PathId(UUID.randomUUID().toString());

        FlowPath forwardPath = new FlowPath();
        forwardPath.setFlowId(flowId);
        forwardPath.setPathId(forwardPathId);
        forwardPath.setSrcSwitch(srcSwitch);
        forwardPath.setDestSwitch(destSwitch);
        forwardPath.setSegments(Collections.emptyList());

        PathId reversePathId = new PathId(UUID.randomUUID().toString());

        FlowPath reversePath = new FlowPath();
        reversePath.setFlowId(flowId);
        reversePath.setPathId(reversePathId);
        reversePath.setSrcSwitch(destSwitch);
        reversePath.setDestSwitch(srcSwitch);
        reversePath.setSegments(Collections.emptyList());

        Flow flow = new Flow();
        flow.setFlowId(flowId);
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(srcPort);
        flow.setSrcVlan(srcVlan);
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(destPort);
        flow.setDestVlan(destVlan);
        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        TransitVlan forwardTransitVlan = new TransitVlan();
        forwardTransitVlan.setFlowId(flowId);
        forwardTransitVlan.setPathId(forwardPathId);
        forwardTransitVlan.setVlan(srcVlan + destVlan + 1);

        TransitVlan reverseTransitVlan = new TransitVlan();
        reverseTransitVlan.setFlowId(flowId);
        reverseTransitVlan.setPathId(reversePathId);
        reverseTransitVlan.setVlan(srcVlan + destVlan + 2);

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
}
