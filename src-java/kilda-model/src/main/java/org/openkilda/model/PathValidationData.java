/* Copyright 2023 Telstra Open Source
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

@Value
@Builder
@AllArgsConstructor
public class PathValidationData {

    Long bandwidth;
    Duration latency;
    Duration latencyTier2;
    List<PathSegmentValidationData> pathSegments;
    String diverseWithFlow;
    String reuseFlowResources;
    FlowEncapsulationType flowEncapsulationType;
    SwitchId srcSwitchId;
    Integer srcPort;
    SwitchId destSwitchId;
    Integer destPort;
    PathComputationStrategy pathComputationStrategy;

    public PathValidationData(PathValidationData pathValidationData, FlowEncapsulationType flowEncapsulationType,
                              PathComputationStrategy pathComputationStrategy) {
        this.bandwidth = pathValidationData.getBandwidth();
        this.latency = pathValidationData.getLatency();
        this.latencyTier2 = pathValidationData.getLatencyTier2();
        this.pathSegments = new LinkedList<>(pathValidationData.getPathSegments());
        this.diverseWithFlow = pathValidationData.getDiverseWithFlow();
        this.reuseFlowResources = pathValidationData.getReuseFlowResources();
        this.srcSwitchId = pathValidationData.getSrcSwitchId();
        this.srcPort = pathValidationData.getSrcPort();
        this.destSwitchId = pathValidationData.getDestSwitchId();
        this.destPort = pathValidationData.getDestPort();

        this.flowEncapsulationType = flowEncapsulationType;
        this.pathComputationStrategy = pathComputationStrategy;
    }

    @Value
    @Builder
    public static class PathSegmentValidationData {
        SwitchId srcSwitchId;
        Integer srcPort;
        SwitchId destSwitchId;
        Integer destPort;
    }
}
