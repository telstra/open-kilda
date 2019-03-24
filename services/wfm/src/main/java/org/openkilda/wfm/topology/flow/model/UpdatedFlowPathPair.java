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

import lombok.Getter;

@Getter
public class UpdatedFlowPathPair extends FlowPathPairWithEncapsulation {
    private FlowPathPairWithEncapsulation oldFlowPair;

    public UpdatedFlowPathPair(FlowPathPairWithEncapsulation oldFlowPair, FlowPathPairWithEncapsulation newFlowPair) {
        super(newFlowPair.getFlow(), newFlowPair.getForwardPath(), newFlowPair.getReversePath(),
                newFlowPair.getForwardTransitVlan(), newFlowPair.getReverseTransitVlan());
        this.oldFlowPair = oldFlowPair;
    }

    /**
     * Returns the forward path of the old flow.
     */
    public FlowPathWithEncapsulation getOldForward() {
        return FlowPathWithEncapsulation.builder()
                .flow(oldFlowPair.getFlow())
                .flowPath(oldFlowPair.getForwardPath())
                .transitVlan(oldFlowPair.getForwardTransitVlan())
                .build();
    }

    /**
     * Returns the reverse path of the old flow.
     */
    public FlowPathWithEncapsulation getOldReverse() {
        return FlowPathWithEncapsulation.builder()
                .flow(oldFlowPair.getFlow())
                .flowPath(oldFlowPair.getReversePath())
                .transitVlan(oldFlowPair.getReverseTransitVlan())
                .build();
    }
}
