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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.payload.network.PathValidationPayload;
import org.openkilda.model.PathValidationData;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

@Mapper
public abstract class PathValidationDataMapper {

    public static PathValidationDataMapper INSTANCE = Mappers.getMapper(PathValidationDataMapper.class);

    /**
     * Converts NB PathValidationDto to messaging PathValidationData.
     * @param pathValidationPayload NB representation of a path validation data
     * @return the messaging representation of a path validation data
     */
    public PathValidationData toPathValidationData(PathValidationPayload pathValidationPayload) {
        List<PathValidationData.PathSegmentValidationData> segments = new LinkedList<>();

        for (int i = 0; i < pathValidationPayload.getNodes().size() - 1; i++) {
            segments.add(PathValidationData.PathSegmentValidationData.builder()
                    .srcSwitchId(pathValidationPayload.getNodes().get(i).getSwitchId())
                    .srcPort(pathValidationPayload.getNodes().get(i).getOutputPort())
                    .destSwitchId(pathValidationPayload.getNodes().get(i + 1).getSwitchId())
                    .destPort(pathValidationPayload.getNodes().get(i + 1).getInputPort())
                    .build());
        }

        return PathValidationData.builder()
                .srcSwitchId(pathValidationPayload.getNodes().get(0).getSwitchId())
                .destSwitchId(
                        pathValidationPayload.getNodes().get(pathValidationPayload.getNodes().size() - 1).getSwitchId())
                .bandwidth(pathValidationPayload.getBandwidth())
                .latency(pathValidationPayload.getLatencyMs() == null ? null :
                        Duration.ofMillis(pathValidationPayload.getLatencyMs()))
                .latencyTier2(pathValidationPayload.getLatencyTier2ms() == null ? null :
                        Duration.ofMillis(pathValidationPayload.getLatencyTier2ms()))
                .diverseWithFlow(pathValidationPayload.getDiverseWithFlow())
                .reuseFlowResources(pathValidationPayload.getReuseFlowResources())
                .flowEncapsulationType(FlowEncapsulationTypeMapper.INSTANCE.toOpenKildaModel(
                        pathValidationPayload.getFlowEncapsulationType()))
                .pathComputationStrategy(pathValidationPayload.getPathComputationStrategy())
                .pathSegments(segments)
                .build();
    }
}
