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

import org.openkilda.messaging.payload.network.PathValidationDto;
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
     * @param pathValidationDto NB representation of a path validation data
     * @return the messaging representation of a path validation data
     */
    public PathValidationData toPathValidationData(PathValidationDto pathValidationDto) {
        List<PathValidationData.PathSegmentValidationData> segments = new LinkedList<>();

        for (int i = 0; i < pathValidationDto.getNodes().size() - 1; i++) {
            segments.add(PathValidationData.PathSegmentValidationData.builder()
                    .srcSwitchId(pathValidationDto.getNodes().get(i).getSwitchId())
                    .srcPort(pathValidationDto.getNodes().get(i).getOutputPort())
                    .destSwitchId(pathValidationDto.getNodes().get(i + 1).getSwitchId())
                    .destPort(pathValidationDto.getNodes().get(i + 1).getInputPort())
                    .build());
        }

        return PathValidationData.builder()
                .srcSwitchId(pathValidationDto.getNodes().get(0).getSwitchId())
                .destSwitchId(pathValidationDto.getNodes().get(pathValidationDto.getNodes().size() - 1).getSwitchId())
                .bandwidth(pathValidationDto.getBandwidth())
                .latency(pathValidationDto.getLatencyMs() == null ? null :
                        Duration.ofMillis(pathValidationDto.getLatencyMs()))
                .latencyTier2(pathValidationDto.getLatencyTier2ms() == null ? null :
                        Duration.ofMillis(pathValidationDto.getLatencyTier2ms()))
                .diverseWithFlow(pathValidationDto.getDiverseWithFlow())
                .reuseFlowResources(pathValidationDto.getReuseFlowResources())
                .flowEncapsulationType(FlowEncapsulationTypeMapper.INSTANCE.toOpenKildaModel(
                        pathValidationDto.getFlowEncapsulationType()))
                .pathComputationStrategy(pathValidationDto.getPathComputationStrategy())
                .pathSegments(segments)
                .build();
    }

}
