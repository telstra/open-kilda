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

package org.openkilda.wfm.topology.flow.model;

import org.openkilda.wfm.share.mappers.FlowPathMapper;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Objects;

@Value
@AllArgsConstructor
public class ReroutedFlowPaths {
    private FlowPathsWithEncapsulation oldFlowPaths;
    private FlowPathsWithEncapsulation newFlowPaths;

    /**
     * Returns is flow has been efficient rerouted flag.
     *
     * @return is rerouted flag.
     */
    public boolean isRerouted() {
        boolean isPrimaryRerouted = !Objects.equals(
                FlowPathMapper.INSTANCE.map(newFlowPaths.getForwardPath()),
                FlowPathMapper.INSTANCE.map(oldFlowPaths.getForwardPath()));
        boolean isProtectedRerouted = newFlowPaths.isAllocateProtectedPath() && oldFlowPaths.isAllocateProtectedPath()
                && !Objects.equals(
                FlowPathMapper.INSTANCE.map(newFlowPaths.getProtectedForwardPath()),
                FlowPathMapper.INSTANCE.map(oldFlowPaths.getProtectedForwardPath()));

        return isPrimaryRerouted || isProtectedRerouted;
    }
}
