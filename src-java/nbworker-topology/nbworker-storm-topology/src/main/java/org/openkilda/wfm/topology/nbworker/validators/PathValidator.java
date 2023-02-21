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

package org.openkilda.wfm.topology.nbworker.validators;

import static org.openkilda.model.PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.messaging.info.network.PathValidationResult;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathSegment;
import org.openkilda.model.PathValidationData;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class PathValidator {

    private final IslRepository islRepository;
    private final FlowRepository flowRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;

    public PathValidator(IslRepository islRepository,
                         FlowRepository flowRepository,
                         SwitchPropertiesRepository switchPropertiesRepository) {
        this.islRepository = islRepository;
        this.flowRepository = flowRepository;
        this.switchPropertiesRepository = switchPropertiesRepository;
    }

    /**
     * Validates whether it is possible to create a path with the given parameters. When there obstacles, this validator
     * returns all errors found on each segment. For example, when there is a path 1-2-3-4 and there is no link between
     * 1 and 2, no sufficient latency between 2 and 3, and not enough bandwidth between 3-4, then this validator returns
     * all 3 errors.
     * @param pathValidationData path parameters to validate.
     * @return a response object containing the validation result and errors if any
     */
    public PathValidationResult validatePath(PathValidationData pathValidationData) {
        Set<String> result = pathValidationData.getPathSegments().stream()
                .map(segment -> executeValidations(
                        new InputData(pathValidationData, segment),
                        new RepositoryData(islRepository, flowRepository, switchPropertiesRepository),
                        getValidations(pathValidationData)))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        return PathValidationResult.builder()
                .isValid(result.isEmpty())
                .errors(new LinkedList<>(result))
                .build();
    }

    private Set<String> executeValidations(InputData inputData,
                                           RepositoryData repositoryData,
                                           List<BiFunction<InputData, RepositoryData, Set<String>>> validations) {

        return validations.stream()
                .map(f -> f.apply(inputData, repositoryData))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private List<BiFunction<InputData, RepositoryData, Set<String>>> getValidations(
            PathValidationData pathValidationData) {
        List<BiFunction<InputData, RepositoryData, Set<String>>> validationFunctions = new LinkedList<>();

        validationFunctions.add(this::validateLink);

        if (pathValidationData.getLatencyMs() != null
                && pathValidationData.getLatencyMs() != 0
                && (pathValidationData.getPathComputationStrategy() == null
                || pathValidationData.getPathComputationStrategy() == LATENCY
                || pathValidationData.getPathComputationStrategy() == MAX_LATENCY)) {
            validationFunctions.add(this::validateLatency);
        }

        if (pathValidationData.getLatencyTier2ms() != null
                && pathValidationData.getLatencyTier2ms() != 0
                && (pathValidationData.getPathComputationStrategy() == null
                || pathValidationData.getPathComputationStrategy() == LATENCY
                || pathValidationData.getPathComputationStrategy() == MAX_LATENCY)) {
            validationFunctions.add(this::validateLatencyTier2);
        }

        if (pathValidationData.getBandwidth() != null
                && pathValidationData.getBandwidth() != 0
                && (pathValidationData.getPathComputationStrategy() == null
                || pathValidationData.getPathComputationStrategy() == COST_AND_AVAILABLE_BANDWIDTH)) {
            validationFunctions.add(this::validateBandwidth);
        }

        if (pathValidationData.getDiverseWithFlow() != null && !pathValidationData.getDiverseWithFlow().isEmpty()) {
            validationFunctions.add(this::validateDiverseWithFlow);
        }

        if (pathValidationData.getFlowEncapsulationType() != null) {
            validationFunctions.add(this::validateEncapsulationType);
        }

        return validationFunctions;
    }

    private Set<String> validateLink(InputData inputData, RepositoryData repositoryData) {
        Optional<Isl> forward = repositoryData.getIslRepository().findByEndpoints(
                inputData.getSegment().getSrcSwitchId(),
                inputData.getSegment().getSrcPort(),
                inputData.getSegment().getDestSwitchId(),
                inputData.getSegment().getDestPort());
        if (!forward.isPresent()) {
            return Collections.singleton(getNoForwardIslError(inputData));
        }

        if (forward.get().getStatus() != IslStatus.ACTIVE) {
            return Collections.singleton(getForwardIslNotActiveError(inputData));
        }

        Optional<Isl> reverse = repositoryData.getIslRepository().findByEndpoints(
                inputData.getSegment().getDestSwitchId(),
                inputData.getSegment().getDestPort(),
                inputData.getSegment().getSrcSwitchId(),
                inputData.getSegment().getSrcPort());
        if (!reverse.isPresent()) {
            return Collections.singleton(getNoReverseIslError(inputData));
        }

        if (reverse.get().getStatus() != IslStatus.ACTIVE) {
            return Collections.singleton(getReverseIslNotActiveError(inputData));
        }

        return Collections.emptySet();
    }

    private Set<String> validateBandwidth(InputData inputData, RepositoryData repositoryData) {
        Optional<Isl> forward = repositoryData.getIslRepository().findByEndpoints(
                inputData.getSegment().getSrcSwitchId(), inputData.getSegment().getSrcPort(),
                inputData.getSegment().getDestSwitchId(), inputData.getSegment().getDestPort());
        if (!forward.isPresent()) {
            return Collections.singleton(getNoForwardIslError(inputData));
        }

        Set<String> errors = new HashSet<>();
        if (getForwardBandwidthWithReusableResources(forward.get(), inputData) < inputData.getPath().getBandwidth()) {
            errors.add(getForwardBandwidthErrorMessage(inputData, forward.get().getAvailableBandwidth()));
        }

        Optional<Isl> reverse = repositoryData.getIslRepository().findByEndpoints(
                inputData.getSegment().getDestSwitchId(), inputData.getSegment().getDestPort(),
                inputData.getSegment().getSrcSwitchId(), inputData.getSegment().getSrcPort());
        if (!reverse.isPresent()) {
            return Collections.singleton(getNoForwardIslError(inputData));
        }
        if (getReverseBandwidthWithReusableResources(reverse.get(), inputData) < inputData.getPath().getBandwidth()) {
            errors.add(getReverseBandwidthErrorMessage(inputData, reverse.get().getAvailableBandwidth()));
        }

        return errors;
    }

    private long getForwardBandwidthWithReusableResources(Isl isl, InputData inputData) {
        return getBandwidthWithReusableResources(inputData, isl,
                pathSegment -> inputData.getSegment().getSrcSwitchId().equals(pathSegment.getSrcSwitchId())
                        && inputData.getSegment().getDestSwitchId().equals(pathSegment.getDestSwitchId())
                        && inputData.getSegment().getSrcPort().equals(pathSegment.getSrcPort())
                        && inputData.getSegment().getDestPort().equals(pathSegment.getDestPort()));
    }

    private long getReverseBandwidthWithReusableResources(Isl isl, InputData inputData) {
        return getBandwidthWithReusableResources(inputData, isl,
                pathSegment -> inputData.getSegment().getSrcSwitchId().equals(pathSegment.getDestSwitchId())
                        && inputData.getSegment().getDestSwitchId().equals(pathSegment.getSrcSwitchId())
                        && inputData.getSegment().getSrcPort().equals(pathSegment.getDestPort())
                        && inputData.getSegment().getDestPort().equals(pathSegment.getSrcPort()));
    }

    private long getBandwidthWithReusableResources(InputData inputData, Isl isl,
                                                   Predicate<PathSegment> pathSegmentPredicate) {
        if (inputData.getPath().getReuseFlowResources() != null
                && !inputData.getPath().getReuseFlowResources().isEmpty()) {

            Optional<Flow> flow = flowRepository.findById(inputData.getPath().getReuseFlowResources());

            Optional<PathSegment> segment = flow.flatMap(value -> value.getPaths().stream()
                    .map(FlowPath::getSegments)
                    .flatMap(List::stream)
                    .filter(pathSegmentPredicate)
                    .findAny());

            return segment.map(s -> isl.getAvailableBandwidth() + s.getBandwidth())
                    .orElseGet(isl::getAvailableBandwidth);
        }

        return isl.getAvailableBandwidth();
    }

    private Set<String> validateEncapsulationType(InputData inputData, RepositoryData repositoryData) {
        Set<String> errors = new HashSet<>();
        Optional<SwitchProperties> srcSwitchProperties = switchPropertiesRepository.findBySwitchId(
                inputData.getPath().getSrcSwitchId());
        if (!srcSwitchProperties.isPresent()) {
            errors.add(getSrcSwitchNotFoundError(inputData));
        }

        srcSwitchProperties.ifPresent(switchProperties -> {
            if (!switchProperties.getSupportedTransitEncapsulation()
                    .contains(inputData.getPath().getFlowEncapsulationType())) {
                errors.add(getSrcSwitchDoesNotSupportEncapsulationTypeError(inputData));
            }
        });

        Optional<SwitchProperties> destSwitchProperties = switchPropertiesRepository.findBySwitchId(
                inputData.getPath().getDestSwitchId());
        if (!destSwitchProperties.isPresent()) {
            errors.add(getDestSwitchNotFoundError(inputData));
        }

        destSwitchProperties.ifPresent(switchProperties -> {
            if (!switchProperties.getSupportedTransitEncapsulation()
                    .contains(inputData.getPath().getFlowEncapsulationType())) {
                errors.add(getDestSwitchDoesNotSupportEncapsulationTypeError(inputData));
            }
        });
        return errors;
    }

    private Set<String> validateDiverseWithFlow(InputData inputData, RepositoryData repositoryData) {
        if (!repositoryData.getIslRepository().findByEndpoints(inputData.getSegment().getSrcSwitchId(),
                inputData.getSegment().getSrcPort(),
                inputData.getSegment().getDestSwitchId(),
                inputData.getSegment().getDestPort()).isPresent()) {
            return Collections.singleton(getNoForwardIslError(inputData));
        }

        Optional<Flow> diverseFlow = flowRepository.findById(inputData.getPath().getDiverseWithFlow());
        if (!diverseFlow.isPresent()) {
            return Collections.singleton(getNoDiverseFlowFoundError(inputData));
        }

        if (diverseFlow.get().getData().getPaths().stream()
                .map(FlowPath::getSegments)
                .flatMap(List::stream)
                .anyMatch(pathSegment -> inputData.getSegment().getSrcSwitchId().equals(pathSegment.getSrcSwitchId())
                        && inputData.getSegment().getDestSwitchId().equals(pathSegment.getDestSwitchId())
                        && inputData.getSegment().getSrcPort().equals(pathSegment.getSrcPort())
                        && inputData.getSegment().getDestPort().equals(pathSegment.getDestPort()))) {
            return Collections.singleton(getNotDiverseSegmentError(inputData));
        }

        return Collections.emptySet();
    }

    private Set<String> validateLatency(InputData inputData, RepositoryData repositoryData) {
        Optional<Isl> isl = repositoryData.getIslRepository().findByEndpoints(
                inputData.getSegment().getSrcSwitchId(), inputData.getSegment().getSrcPort(),
                inputData.getSegment().getDestSwitchId(), inputData.getSegment().getDestPort());
        if (!isl.isPresent()) {
            return Collections.singleton(getNoForwardIslError(inputData));
        }
        // TODO make sure that comparing numbers of the same order
        if (isl.get().getLatency() > inputData.getPath().getLatencyMs()) {
            return Collections.singleton(getLatencyErrorMessage(inputData, isl.get().getLatency()));
        }

        return Collections.emptySet();
    }

    private Set<String> validateLatencyTier2(InputData inputData, RepositoryData repositoryData) {
        Optional<Isl> isl = repositoryData.getIslRepository().findByEndpoints(
                inputData.getSegment().getSrcSwitchId(), inputData.getSegment().getSrcPort(),
                inputData.getSegment().getDestSwitchId(), inputData.getSegment().getDestPort());
        if (!isl.isPresent()) {
            return Collections.singleton(getNoForwardIslError(inputData));
        }
        // TODO make sure that comparing numbers of the same orders
        if (isl.get().getLatency() > inputData.getPath().getLatencyTier2ms()) {
            return Collections.singleton(getLatencyTier2ErrorMessage(inputData, isl.get().getLatency()));
        }

        return Collections.emptySet();
    }

    private String getLatencyTier2ErrorMessage(InputData data, long actualLatency) {
        return String.format(
                "Requested latency tier 2 is too low between source switch %s port %d and destination switch"
                        + " %s port %d. Requested %d, but the link supports %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getPath().getLatencyTier2ms(), actualLatency);
    }

    private String getLatencyErrorMessage(InputData data, long actualLatency) {
        return String.format(
                "Requested latency is too low between source switch %s port %d and destination switch %s port %d."
                        + " Requested %d, but the link supports %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getPath().getLatencyMs(), actualLatency);
    }

    private String getForwardBandwidthErrorMessage(InputData data, long actualBandwidth) {
        return String.format(
                "There is not enough bandwidth between the source switch %s port %d and destination switch %s port %d"
                        + " (forward path). Requested bandwidth %d, but the link supports %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getPath().getBandwidth(), actualBandwidth);
    }

    private String getReverseBandwidthErrorMessage(InputData data, long actualBandwidth) {
        return String.format(
                "There is not enough bandwidth between the source switch %s port %d and destination switch %s port %d"
                        + " (reverse path). Requested bandwidth %d, but the link supports %d",
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getPath().getBandwidth(), actualBandwidth);
    }

    private String getNoForwardIslError(InputData data) {
        return String.format(
                "There is no ISL between source switch %s port %d and destination switch %s port %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort());
    }

    private String getNoReverseIslError(InputData data) {
        return String.format(
                "There is no ISL between source switch %s port %d and destination switch %s port %d",
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort());
    }

    private String getForwardIslNotActiveError(InputData data) {
        return String.format(
                "The ISL is not in ACTIVE state between source switch %s port %d and destination switch %s port %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort());
    }

    private String getReverseIslNotActiveError(InputData data) {
        return String.format(
                "The ISL is not in ACTIVE state between source switch %s port %d and destination switch %s port %d",
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort());
    }

    private String getNoDiverseFlowFoundError(InputData data) {
        return String.format("Could not find the diverse flow with ID %s", data.getPath().getDiverseWithFlow());
    }

    private String getNotDiverseSegmentError(InputData data) {
        return String.format("The following segment intersects with the flow %s: source switch %s port %d and "
                        + "destination switch %s port %d",
                data.getPath().getDiverseWithFlow(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort());
    }

    private String getSrcSwitchNotFoundError(InputData data) {
        return String.format("The following switch has not been found: %s", data.getSegment().getSrcSwitchId());
    }

    private String getDestSwitchNotFoundError(InputData data) {
        return String.format("The following switch has not been found: %s", data.getSegment().getDestSwitchId());
    }

    private String getSrcSwitchDoesNotSupportEncapsulationTypeError(InputData data) {
        return String.format("The switch %s doesn't support encapsulation type %s",
                data.getSegment().getSrcSwitchId(), data.getPath().getFlowEncapsulationType());
    }

    private String getDestSwitchDoesNotSupportEncapsulationTypeError(InputData data) {
        return String.format("The switch %s doesn't support encapsulation type %s",
                data.getSegment().getDestSwitchId(), data.getPath().getFlowEncapsulationType());
    }

    @Getter
    @AllArgsConstructor
    private static class InputData {
        PathValidationData path;
        PathValidationData.PathSegmentValidationData segment;
    }

    @Getter
    @AllArgsConstructor
    private static class RepositoryData {
        IslRepository islRepository;
        FlowRepository flowRepository;
        SwitchPropertiesRepository switchPropertiesRepository;
    }
}
