/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.flow;

import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.UnidirectionalFlow;

import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.UUID;

@Setter
@Accessors(fluent = true)
public class TestFlowBuilder {

    private String flowId = UUID.randomUUID().toString();
    private Switch srcSwitch;
    private int srcPort;
    private int srcVlan;
    private Switch destSwitch;
    private int destPort;
    private int destVlan;
    private Long cookie = null;
    private long unmaskedCookie = 1;
    private long bandwidth;
    private boolean ignoreBandwidth = false;
    private int transitVlan = 0;
    private Integer meterId = null;
    private FlowStatus status = FlowStatus.UP;
    private Integer maxLatency = null;
    private Integer priority = null;
    private FlowEncapsulationType encapsulationType;

    public TestFlowBuilder() {
    }

    public TestFlowBuilder(String flowId) {
        this.flowId = flowId;
    }

    /**
     * Build a Flow with set properties.
     */
    public Flow build() {
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .destVlan(destVlan)
                .bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .encapsulationType(encapsulationType)
                .maxLatency(maxLatency)
                .priority(priority)
                .build();
        flow.setStatus(status);

        FlowPath forwardPath =
                buildFlowPath(flow, srcSwitch, destSwitch,
                        cookie != null ? new Cookie(cookie) : Cookie.buildForwardCookie(unmaskedCookie),
                        meterId != null ? new MeterId(meterId) : null);
        FlowPath reversePath =
                buildFlowPath(flow, destSwitch, srcSwitch, Cookie.buildReverseCookie(unmaskedCookie), null);

        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        return flow;
    }

    /**
     * Build a UnidirectionalFlow with set properties.
     */
    public UnidirectionalFlow buildUnidirectionalFlow() {
        Flow flow = build();

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan forwardTransitVlan = TransitVlan.builder()
                .flowId(flowId)
                .pathId(flow.getForwardPath().getPathId())
                .vlan(transitVlan > 0 ? transitVlan : srcVlan + destVlan + 1)
                .build();

        return new UnidirectionalFlow(flow.getForwardPath(), forwardTransitVlan, true);
    }

    private FlowPath buildFlowPath(Flow flow, Switch pathSrc, Switch pathDest, Cookie cookie, MeterId meterId) {
        return FlowPath.builder()
                .flow(flow)
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(pathSrc)
                .destSwitch(pathDest)
                .segments(Collections.emptyList())
                .cookie(cookie)
                .meterId(meterId)
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .build();
    }
}
