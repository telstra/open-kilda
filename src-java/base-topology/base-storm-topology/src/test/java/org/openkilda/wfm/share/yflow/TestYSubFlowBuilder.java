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

package org.openkilda.wfm.share.yflow;

import static com.google.common.base.Preconditions.checkArgument;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;

import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

@Setter
@Accessors(fluent = true)
public class TestYSubFlowBuilder {
    private YFlow yFlow;
    private Flow flow;
    private int sharedEndpointVlan;
    private int sharedEndpointInnerVlan;

    private FlowEndpoint endpoint;

    private Instant timeCreate;
    private Instant timeModify;

    /**
     * Build {@link YSubFlow} instance.
     */
    public YSubFlow build() {
        checkArgument(endpoint != null, "YSubFlow endpoint must be defined");

        YSubFlow subFlow = YSubFlow.builder()
                .yFlow(yFlow)
                .flow(flow)
                .sharedEndpointVlan(sharedEndpointVlan)
                .sharedEndpointInnerVlan(sharedEndpointInnerVlan)
                .endpointSwitchId(endpoint.getSwitchId())
                .endpointPort(endpoint.getPortNumber())
                .endpointVlan(endpoint.getOuterVlanId())
                .endpointInnerVlan(endpoint.getInnerVlanId())
                .build();

        subFlow.setTimeCreate(timeCreate);
        subFlow.setTimeModify(timeModify);

        return subFlow;
    }
}
