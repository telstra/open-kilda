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

package org.openkilda.wfm.topology.flow.model;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.TransitVlan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
public class FlowPathPairWithEncapsulation {
    private Flow flow;
    private FlowPath forwardPath;
    private FlowPath reversePath;
    private FlowPath protectedForwardPath;
    private FlowPath protectedReversePath;

    private TransitVlan forwardTransitVlan;
    private TransitVlan reverseTransitVlan;
    private TransitVlan protectedForwardTransitVlan;
    private TransitVlan protectedReverseTransitVlan;

    /**
     * Returns the forward path of the flow.
     */
    public FlowPathWithEncapsulation getForward() {
        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(forwardPath)
                .transitVlan(forwardTransitVlan)
                .build();
    }

    /**
     * Returns the protected forward path of the flow.
     */
    public FlowPathWithEncapsulation getProtectedForward() {
        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(protectedForwardPath)
                .transitVlan(protectedForwardTransitVlan)
                .build();
    }

    /**
     * Returns the reverse path of the flow.
     */
    public FlowPathWithEncapsulation getReverse() {
        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(reversePath)
                .transitVlan(reverseTransitVlan)
                .build();
    }

    /**
     * Returns the protected reverse path of the flow.
     */
    public FlowPathWithEncapsulation getProtectedReverse() {
        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(protectedReversePath)
                .transitVlan(protectedReverseTransitVlan)
                .build();
    }
}
