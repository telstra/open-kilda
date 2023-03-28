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

import org.openkilda.messaging.command.flow.PathValidateRequest;
import org.openkilda.messaging.info.network.PathValidationResult;
import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
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
import org.openkilda.pce.mapper.PathSegmentMapper;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.error.SwitchPropertiesNotFoundException;
import org.openkilda.wfm.share.mappers.PathMapper;
import org.openkilda.wfm.share.mappers.PathValidationDataMapper;
import org.openkilda.wfm.topology.nbworker.validators.PathValidator;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class PathsService {
    private final int defaultMaxPathCount;
    private final PathComputer pathComputer;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final IslRepository islRepository;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;

    public PathsService(RepositoryFactory repositoryFactory, PathComputerConfig pathComputerConfig) {
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        PathComputerFactory pathComputerFactory = new PathComputerFactory(
                pathComputerConfig, new AvailableNetworkFactory(pathComputerConfig, repositoryFactory));
        pathComputer = pathComputerFactory.getPathComputer();
        defaultMaxPathCount = pathComputerConfig.getMaxPathCount();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
    }

    /**
     * Get paths based on the given parameters. Some parameters might be defaulted using OpenKilda configuration.
     */
    public List<PathsInfoData> getPaths(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FlowEncapsulationType requestEncapsulationType,
            PathComputationStrategy requestPathComputationStrategy, Duration maxLatency, Duration maxLatencyTier2,
            Integer maxPathCount) throws RecoverableException, SwitchNotFoundException, UnroutableFlowException {

        return getPaths(
                validateAndDefaultGetPathRequestParameters(
                    srcSwitchId,
                    dstSwitchId,
                    requestEncapsulationType,
                    requestPathComputationStrategy,
                    maxLatency,
                    maxLatencyTier2,
                    maxPathCount)
                )
                .stream()
                .map(PathMapper.INSTANCE::map)
                .map(path -> PathsInfoData.builder().path(path).build())
                .collect(Collectors.toList());
    }

    private List<Path> getPaths(GetPathsRequestParameters getPathsRequestParameters)
            throws UnroutableFlowException, RecoverableException {
        return pathComputer.getNPaths(
                getPathsRequestParameters.getSrcSwitchId(),
                getPathsRequestParameters.getDstSwitchId(),
                getPathsRequestParameters.getMaxPathCount(),
                getPathsRequestParameters.getFlowEncapsulationType(),
                getPathsRequestParameters.getPathComputationStrategy(),
                getPathsRequestParameters.getMaxLatency(),
                getPathsRequestParameters.getMaxLatencyTier2());
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
     * @throws SwitchNotFoundException an exception from PCE
     * @throws UnroutableFlowException an exception from PCE
     */
    public List<PathsInfoData> getPathsWithProtectedPath(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FlowEncapsulationType requestEncapsulationType,
            PathComputationStrategy requestPathComputationStrategy, Duration maxLatency, Duration maxLatencyTier2,
            Integer maxPathCount)
            throws RecoverableException, SwitchNotFoundException, UnroutableFlowException {

        GetPathsRequestParameters getPathsRequestParameters = validateAndDefaultGetPathRequestParameters(
                srcSwitchId,
                dstSwitchId,
                requestEncapsulationType,
                requestPathComputationStrategy,
                maxLatency,
                maxLatencyTier2,
                maxPathCount);

        return getPaths(getPathsRequestParameters)
                .stream()
                .map(path ->
                    Path.builder()
                            .segments(path.getSegments())
                            .isBackupPath(path.isBackupPath())
                            .minAvailableBandwidth(path.getMinAvailableBandwidth())
                            .latency(path.getLatency())
                            .srcSwitchId(path.getSrcSwitchId())
                            .destSwitchId(path.getDestSwitchId())
                            .protectedPath(getProtectedPath(path, getPathsRequestParameters))
                            .build())
                .map(PathMapper.INSTANCE::map)
                .map(path -> PathsInfoData.builder().path(path).build())
                .collect(Collectors.toList());
    }

    private Path getProtectedPath(Path path, GetPathsRequestParameters getPathsRequestParameters) {
        Flow flow = createVirtualFlow(path,
                getPathsRequestParameters.getFlowEncapsulationType(),
                getPathsRequestParameters.getPathComputationStrategy(),
                getPathsRequestParameters.getMaxLatency(),
                getPathsRequestParameters.getMaxLatencyTier2());

        GetPathsResult pathsResult = pathComputer.getProtectedPath(flow, Collections.emptyList());
        if (pathsResult.isSuccess() && pathsResult.getForward().equals(path)) {
            log.error("Protected path is equal to the main path!");
            // TODO investigate: throw new IllegalStateException("Protected path is equal to the main path!");
        }

        return pathsResult.isSuccess() ? pathsResult.getForward() : null;
    }

    private Flow createVirtualFlow(Path path,
                                   FlowEncapsulationType encapsulationType,
                                   PathComputationStrategy pathComputationStrategy,
                                   Duration maxLatency,
                                   Duration maxLatencyTier2) {
        String diverseGroupId = UUID.randomUUID().toString();
        if (!flowPathRepository.findByFlowGroupId(diverseGroupId).isEmpty()) {
            log.error("There is a flow with the same diverse group ID as the virtual flow. group ID: {}",
                    diverseGroupId);
        }

        Flow flow = Flow.builder()
                .description("A virtual flow for computing a protected path to this flow")
                .flowId("")
                .srcSwitch(Switch.builder().switchId(path.getSrcSwitchId()).build())
                .destSwitch(Switch.builder().switchId(path.getDestSwitchId()).build())
                .bandwidth(path.getMinAvailableBandwidth())
                .encapsulationType(encapsulationType)
                .maxLatency(maxLatency != null ? maxLatency.toNanos() : null)
                .maxLatencyTier2(maxLatencyTier2 != null ? maxLatencyTier2.toNanos() : null)
                .pathComputationStrategy(pathComputationStrategy)
                .diverseGroupId(diverseGroupId)
                .build();

        flow.setForwardPath(FlowPath.builder()
                .pathId(new PathId("A virtual forward flow path for computing a protected path"))
                .latency(path.getLatency())
                .bandwidth(path.getMinAvailableBandwidth())
                .srcSwitch(Switch.builder().switchId(path.getSrcSwitchId()).build())
                .destSwitch(Switch.builder().switchId(path.getDestSwitchId()).build())
                .segments(PathSegmentMapper.INSTANCE.toPathSegmentList(path.getSegments()))
                .build());

        flow.setReversePath(FlowPath.builder()
                .pathId(new PathId("A virtual reverse flow path for computing a protected path"))
                .latency(path.getLatency())
                .bandwidth(path.getMinAvailableBandwidth())
                .srcSwitch(Switch.builder().switchId(path.getDestSwitchId()).build())
                .destSwitch(Switch.builder().switchId(path.getSrcSwitchId()).build())
                .segments(PathSegmentMapper.INSTANCE.toPathSegmentList(path.getSegments()))
                .build());

        return flow;
    }

    private GetPathsRequestParameters validateAndDefaultGetPathRequestParameters(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FlowEncapsulationType requestEncapsulationType,
            PathComputationStrategy requestPathComputationStrategy, Duration maxLatency, Duration maxLatencyTier2,
            Integer maxPathCount) throws SwitchNotFoundException {
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

        return GetPathsRequestParameters.builder()
                .srcSwitchId(srcSwitchId)
                .dstSwitchId(dstSwitchId)
                .maxPathCount(maxPathCount)
                .flowEncapsulationType(flowEncapsulationType)
                .pathComputationStrategy(pathComputationStrategy)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .build();
    }

    /**
     * This method validates a path and collects errors if any. Validations depend on the information in the request.
     * For example, if the request doesn't contain latency, the path will not be validated using max latency strategy.
     * @param request request containing the path and parameters to validate
     * @return a response with the success or the list of errors
     */
    public List<PathValidationResult> validatePath(PathValidateRequest request) {
        PathValidator pathValidator = new PathValidator(islRepository,
                flowRepository,
                switchPropertiesRepository,
                switchRepository,
                kildaConfigurationRepository.getOrDefault());

        return Collections.singletonList(pathValidator.validatePath(
                PathValidationDataMapper.INSTANCE.toPathValidationData(request.getPathValidationPayload())
        ));
    }

    @Value
    @Builder
    private static class GetPathsRequestParameters {
        SwitchId srcSwitchId;
        SwitchId dstSwitchId;
        FlowEncapsulationType flowEncapsulationType;
        PathComputationStrategy pathComputationStrategy;
        Duration maxLatency;
        Duration maxLatencyTier2;
        Integer maxPathCount;
    }
}
