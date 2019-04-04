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
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
public class FlowPathPairWithEncapsulation {
    private final Flow flow;
    private final FlowPath forwardPath;
    private final FlowPath reversePath;
    private final EncapsulationResources forwardEncapsulation;
    private final EncapsulationResources reverseEncapsulation;

    /**
     * Returns the forward path of the flow.
     */
    public FlowPathWithEncapsulation getForward() {
        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(forwardPath)
                .encapsulation(forwardEncapsulation)
                .build();
    }

    /**
     * Returns the reverse path of the flow.
     */
    public FlowPathWithEncapsulation getReverse() {
        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(reversePath)
                .encapsulation(reverseEncapsulation)
                .build();
    }
}
