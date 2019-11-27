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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.model.system.KildaConfigurationDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link KildaConfiguration} to {@link KildaConfigurationDto} and back.
 */
@Mapper
public abstract class KildaConfigurationMapper {

    public static final KildaConfigurationMapper INSTANCE = Mappers.getMapper(KildaConfigurationMapper.class);

    /**
     * Convert {@link KildaConfiguration} to {@link KildaConfigurationDto}.
     */
    public KildaConfigurationDto map(KildaConfiguration kildaConfiguration) {
        if (kildaConfiguration == null) {
            return null;
        }
        String flowEncapsulationType = kildaConfiguration.getFlowEncapsulationType() == null ? null :
                kildaConfiguration.getFlowEncapsulationType().name().toLowerCase();
        String pathComputationStrategy = kildaConfiguration.getPathComputationStrategy() == null ? null :
                kildaConfiguration.getPathComputationStrategy().name().toLowerCase();
        return KildaConfigurationDto.builder()
                .flowEncapsulationType(flowEncapsulationType)
                .useMultiTable(kildaConfiguration.getUseMultiTable())
                .pathComputationStrategy(pathComputationStrategy)
                .build();
    }

    /**
     * Convert {@link KildaConfigurationDto} to {@link KildaConfiguration}.
     */
    public KildaConfiguration map(KildaConfigurationDto kildaConfigurationDto) {
        if (kildaConfigurationDto == null) {
            return null;
        }
        String flowEncapsulationType = kildaConfigurationDto.getFlowEncapsulationType();
        String pathComputationStrategy = kildaConfigurationDto.getPathComputationStrategy();
        return KildaConfiguration.builder()
                .flowEncapsulationType(
                        flowEncapsulationType == null ? null
                                : FlowEncapsulationType.valueOf(flowEncapsulationType.toUpperCase()))
                .pathComputationStrategy(
                        pathComputationStrategy == null ? null
                                : PathComputationStrategy.valueOf(pathComputationStrategy.toUpperCase()))
                .useMultiTable(kildaConfigurationDto.getUseMultiTable())
                .build();
    }
}
