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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.error.SwitchPropertiesNotFoundException;
import org.openkilda.wfm.share.mappers.PathMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class PathsService {
    private final int defaultMaxPathCount;
    private PathComputer pathComputer;
    private SwitchRepository switchRepository;
    private SwitchPropertiesRepository switchPropertiesRepository;
    private KildaConfigurationRepository kildaConfigurationRepository;

    public PathsService(RepositoryFactory repositoryFactory, PathComputerConfig pathComputerConfig) {
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        PathComputerFactory pathComputerFactory = new PathComputerFactory(
                pathComputerConfig, new AvailableNetworkFactory(pathComputerConfig, repositoryFactory));
        pathComputer = pathComputerFactory.getPathComputer();
        defaultMaxPathCount = pathComputerConfig.getMaxPathCount();
    }

    /**
     * Get paths.
     */
    public List<PathsInfoData> getPaths(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FlowEncapsulationType requestEncapsulationType,
            PathComputationStrategy requestPathComputationStrategy, Duration maxLatency, Duration maxLatencyTier2,
            Integer maxPathCount) throws RecoverableException, SwitchNotFoundException, UnroutableFlowException {
        if (Objects.equals(srcSwitchId, dstSwitchId)) {
            throw new IllegalArgumentException(
                    String.format("Source and destination switch IDs are equal: '%s'", srcSwitchId));
        }
        if (!switchRepository.exists(srcSwitchId)) {
            throw new SwitchNotFoundException(srcSwitchId);
        }
        if (!switchRepository.exists(dstSwitchId)) {
            throw new SwitchNotFoundException(dstSwitchId);
        }

        KildaConfiguration kildaConfiguration = kildaConfigurationRepository.getOrDefault();
        FlowEncapsulationType flowEncapsulationType = Optional.ofNullable(requestEncapsulationType)
                .orElse(kildaConfiguration.getFlowEncapsulationType());

        SwitchProperties srcProperties = switchPropertiesRepository.findBySwitchId(srcSwitchId).orElseThrow(
                () -> new SwitchPropertiesNotFoundException(srcSwitchId));
        if (!srcProperties.getSupportedTransitEncapsulation().contains(flowEncapsulationType)) {
            throw new IllegalArgumentException(String.format("Switch %s doesn't support %s encapslation type. Choose "
                    + "one of the supported encapsulation types %s or update switch properties and add needed "
                    + "encapsulation type.", srcSwitchId, flowEncapsulationType,
                    srcProperties.getSupportedTransitEncapsulation()));
        }

        SwitchProperties dstProperties = switchPropertiesRepository.findBySwitchId(dstSwitchId).orElseThrow(
                () -> new SwitchPropertiesNotFoundException(dstSwitchId));
        if (!dstProperties.getSupportedTransitEncapsulation().contains(flowEncapsulationType)) {
            throw new IllegalArgumentException(String.format("Switch %s doesn't support %s encapslation type. Choose "
                    + "one of the supported encapsulation types %s or update switch properties and add needed "
                    + "encapsulation type.", dstSwitchId, requestEncapsulationType,
                    dstProperties.getSupportedTransitEncapsulation()));
        }

        PathComputationStrategy pathComputationStrategy = Optional.ofNullable(requestPathComputationStrategy)
                .orElse(kildaConfiguration.getPathComputationStrategy());

        if (maxPathCount == null) {
            maxPathCount = defaultMaxPathCount;
        }
        List<Path> flowPaths = pathComputer.getNPaths(srcSwitchId, dstSwitchId, maxPathCount, flowEncapsulationType,
                pathComputationStrategy, maxLatency, maxLatencyTier2);

        return flowPaths.stream().map(PathMapper.INSTANCE::map)
                .map(path -> PathsInfoData.builder().path(path).build())
                .collect(Collectors.toList());
    }
}
