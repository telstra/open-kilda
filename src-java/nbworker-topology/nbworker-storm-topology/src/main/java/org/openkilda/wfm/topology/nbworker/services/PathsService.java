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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.GetPathsResult;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class PathsService {
    private final int defaultMaxPathCount;
    private final PathComputer pathComputer;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final KildaConfigurationRepository kildaConfigurationRepository;

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

        List<Path> flowPaths = validateInputAndGetPaths(srcSwitchId, dstSwitchId, requestEncapsulationType,
                requestPathComputationStrategy, maxLatency, maxLatencyTier2, maxPathCount);

        return flowPaths.stream().map(PathMapper.INSTANCE::map)
                .map(path -> PathsInfoData.builder().path(path).build())
                .collect(Collectors.toList());
    }

    /**
     * This method finds paths between the given switches with the given constraints. This version calculates whether
     * a protected path can be created for each found path.
     *
     * @param srcSwitchId a source switch ID
     * @param dstSwitchId a destination switch ID
     * @param requestEncapsulationType encapsulation type from the request
     * @param requestPathComputationStrategy path computation strategy from the request
     * @param maxLatency max latency from the request
     * @param maxLatencyTier2 max latency tier 2 from the request
     * @param maxPathCount find no more than this number of paths
     * @return a list of paths.
     * @throws RecoverableException an exception from path computer
     * @throws SwitchNotFoundException an exception from path computer
     * @throws UnroutableFlowException an exception from path computer
     */
    public List<PathsInfoData> getPathsWithProtectedPathAvailability(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FlowEncapsulationType requestEncapsulationType,
            PathComputationStrategy requestPathComputationStrategy, Duration maxLatency, Duration maxLatencyTier2,
            Integer maxPathCount)
            throws RecoverableException, SwitchNotFoundException, UnroutableFlowException {

        return validateInputAndGetPaths(srcSwitchId, dstSwitchId, requestEncapsulationType,
                requestPathComputationStrategy, maxLatency, maxLatencyTier2, maxPathCount)
                .stream()
                .map(path ->
                    Path.builder()
                            .segments(path.getSegments())
                            .isBackupPath(path.isBackupPath())
                            .minAvailableBandwidth(path.getMinAvailableBandwidth())
                            .latency(path.getLatency())
                            .srcSwitchId(path.getSrcSwitchId())
                            .destSwitchId(path.getDestSwitchId())
                            // TODO add necessary parameters to Path and refactor this
                            .protectedPath(getProtectedPathAvailableFor(path, requestEncapsulationType,
                                    requestPathComputationStrategy, maxLatency, maxLatencyTier2).getForward())
                            .build())
                .map(PathMapper.INSTANCE::map)
                .map(path -> PathsInfoData.builder().path(path).build())
                .collect(Collectors.toList());
    }

    private GetPathsResult getProtectedPathAvailableFor(Path path, FlowEncapsulationType encapsulationType,
                                                        PathComputationStrategy pathComputationStrategy,
                                                        Duration maxLatency, Duration maxLatencyTier2) {
        Flow flow = Flow.builder()
                .description("A virtual flow for computing a protected path to this flow")
                .flowId("")
                .srcSwitch(Switch.builder().switchId(path.getSrcSwitchId()).build())
                .destSwitch(Switch.builder().switchId(path.getDestSwitchId()).build())
                .maxLatency(path.getLatency())
                .bandwidth(path.getMinAvailableBandwidth())
                .encapsulationType(encapsulationType)
                .maxLatency(maxLatency.toNanos())
                .maxLatencyTier2(maxLatencyTier2.toNanos())
                .pathComputationStrategy(pathComputationStrategy)
                .build();
        try {
            return pathComputer.getPath(flow, Collections.emptyList(), true);
        } catch (RecoverableException | UnroutableFlowException e) {
            return GetPathsResult.builder().build();
        }
    }

    private List<Path> validateInputAndGetPaths(
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
            throw new IllegalArgumentException(String.format("Switch %s doesn't support %s encapsulation type. Choose "
                            + "one of the supported encapsulation types %s or update switch properties and add needed "
                            + "encapsulation type.", srcSwitchId, flowEncapsulationType,
                    srcProperties.getSupportedTransitEncapsulation()));
        }

        SwitchProperties dstProperties = switchPropertiesRepository.findBySwitchId(dstSwitchId).orElseThrow(
                () -> new SwitchPropertiesNotFoundException(dstSwitchId));
        if (!dstProperties.getSupportedTransitEncapsulation().contains(flowEncapsulationType)) {
            throw new IllegalArgumentException(String.format("Switch %s doesn't support %s encapsulation type. Choose "
                            + "one of the supported encapsulation types %s or update switch properties and add needed "
                            + "encapsulation type.", dstSwitchId, requestEncapsulationType,
                    dstProperties.getSupportedTransitEncapsulation()));
        }

        PathComputationStrategy pathComputationStrategy = Optional.ofNullable(requestPathComputationStrategy)
                .orElse(kildaConfiguration.getPathComputationStrategy());

        if (maxPathCount == null) {
            maxPathCount = defaultMaxPathCount;
        }

        if (maxPathCount <= 0) {
            throw new IllegalArgumentException(String.format("Incorrect maxPathCount: %s", maxPathCount));
        }

        return pathComputer.getNPaths(srcSwitchId, dstSwitchId, maxPathCount, flowEncapsulationType,
                pathComputationStrategy, maxLatency, maxLatencyTier2);
    }
}
