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
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathSegment;
import org.openkilda.model.PathValidationData;
import org.openkilda.model.PathValidationData.PathSegmentValidationData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PathValidator {

    public static final String LATENCY = "latency";
    public static final String LATENCY_TIER_2 = "latency tier 2";
    private final IslRepository islRepository;
    private final FlowRepository flowRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;

    private final SwitchRepository switchRepository;

    public PathValidator(IslRepository islRepository,
                         FlowRepository flowRepository,
                         SwitchPropertiesRepository switchPropertiesRepository,
                         SwitchRepository switchRepository) {
        this.islRepository = islRepository;
        this.flowRepository = flowRepository;
        this.switchPropertiesRepository = switchPropertiesRepository;
        this.switchRepository = switchRepository;
    }

    /**
     * Validates whether it is possible to create a path with the given parameters. When there are obstacles, this
     * validator returns all errors found on each segment. For example, when there is a path 1-2-3-4 and there is
     * no link between 1 and 2, no sufficient latency between 2 and 3, and not enough bandwidth between 3-4, then
     * this validator returns all 3 errors.
     *
     * @param pathValidationData path parameters to validate.
     * @return a response object containing the validation result and errors if any
     */
    public PathValidationResult validatePath(PathValidationData pathValidationData) {
        Set<String> result = pathValidationData.getPathSegments().stream()
                .map(segment -> executeValidations(
                        new InputData(pathValidationData, segment),
                        new RepositoryData(islRepository, flowRepository, switchPropertiesRepository, switchRepository),
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

        validationFunctions.add(this::validateForwardAndReverseLinks);

        if (isLatencyValidationRequired(pathValidationData)) {
            validationFunctions.add(this::validateLatency);
        }

        if (isLatencyTier2ValidationRequired(pathValidationData)) {
            validationFunctions.add(this::validateLatencyTier2);
        }

        if (isBandwidthValidationRequired(pathValidationData)) {
            validationFunctions.add(this::validateBandwidth);
        }

        if (isDiverseWithFlowValidationRequired(pathValidationData)) {
            validationFunctions.add(this::validateDiverseWithFlow);
        }

        if (isEncapsulationTypeValidationRequired(pathValidationData)) {
            validationFunctions.add(this::validateEncapsulationType);
        }

        return validationFunctions;
    }

    private boolean isEncapsulationTypeValidationRequired(PathValidationData pathValidationData) {
        return pathValidationData.getFlowEncapsulationType() != null;
    }

    private boolean isDiverseWithFlowValidationRequired(PathValidationData pathValidationData) {
        return StringUtils.isNotBlank(pathValidationData.getDiverseWithFlow());
    }

    private boolean isBandwidthValidationRequired(PathValidationData pathValidationData) {
        return pathValidationData.getBandwidth() != null
                && pathValidationData.getBandwidth() != 0
                && (pathValidationData.getPathComputationStrategy() == null
                || pathValidationData.getPathComputationStrategy() == COST_AND_AVAILABLE_BANDWIDTH);
    }

    private boolean isLatencyTier2ValidationRequired(PathValidationData pathValidationData) {
        return pathValidationData.getLatencyTier2() != null
                && !pathValidationData.getLatencyTier2().isZero()
                && (pathValidationData.getPathComputationStrategy() == null
                || pathValidationData.getPathComputationStrategy() == PathComputationStrategy.LATENCY
                || pathValidationData.getPathComputationStrategy() == MAX_LATENCY);
    }

    private boolean isLatencyValidationRequired(PathValidationData pathValidationData) {
        return pathValidationData.getLatency() != null
                && !pathValidationData.getLatency().isZero()
                && (pathValidationData.getPathComputationStrategy() == null
                || pathValidationData.getPathComputationStrategy() == PathComputationStrategy.LATENCY
                || pathValidationData.getPathComputationStrategy() == MAX_LATENCY);
    }

    private Set<String> validateForwardAndReverseLinks(InputData inputData, RepositoryData repositoryData) {
        Map<SwitchId, Switch> switchMap = repositoryData.getSwitchRepository().findByIds(
                Sets.newHashSet(inputData.getSegment().getSrcSwitchId(), inputData.getSegment().getDestSwitchId()));
        Set<String> errors = Sets.newHashSet();
        if (!switchMap.containsKey(inputData.getSegment().getSrcSwitchId())) {
            errors.add(getSrcSwitchNotFoundError(inputData));
        }
        if (!switchMap.containsKey(inputData.getSegment().getDestSwitchId())) {
            errors.add(getDestSwitchNotFoundError(inputData));
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        errors.addAll(validateForwardLink(inputData, repositoryData));
        errors.addAll(validateReverseLink(inputData, repositoryData));

        return errors;
    }

    private Set<String> validateForwardLink(InputData inputData, RepositoryData repositoryData) {
        return validateLink(
                inputData.getSegment().getSrcSwitchId(),
                inputData.getSegment().getSrcPort(),
                inputData.getSegment().getDestSwitchId(),
                inputData.getSegment().getDestPort(),
                repositoryData, inputData,
                this::getNoForwardIslError, this::getForwardIslNotActiveError);
    }

    private Set<String> validateReverseLink(InputData inputData, RepositoryData repositoryData) {
        return validateLink(
                inputData.getSegment().getDestSwitchId(),
                inputData.getSegment().getDestPort(),
                inputData.getSegment().getSrcSwitchId(),
                inputData.getSegment().getSrcPort(),
                repositoryData, inputData,
                this::getNoReverseIslError, this::getReverseIslNotActiveError);
    }

    private Set<String> validateLink(SwitchId srcSwitchId, int srcPort, SwitchId destSwitchId, int destPort,
                                                       RepositoryData repositoryData, InputData inputData,
                                                       Function<InputData, String> noLinkErrorProducer,
                                                       Function<InputData, String> linkNotActiveErrorProducer) {
        Optional<Isl> isl = getIslByEndPoints(srcSwitchId, srcPort, destSwitchId, destPort, repositoryData);
        Set<String> errors = Sets.newHashSet();
        if (!isl.isPresent()) {
            errors.add(noLinkErrorProducer.apply(inputData));
        }
        if (isl.isPresent() && isl.get().getStatus() != IslStatus.ACTIVE) {
            errors.add(linkNotActiveErrorProducer.apply(inputData));
        }

        return errors;
    }

    private Optional<Isl> getIslByEndPoints(SwitchId srcSwitchId, int srcPort, SwitchId destSwitchId, int destPort,
                                            RepositoryData repositoryData) {
        return repositoryData.getIslRepository().findByEndpoints(srcSwitchId, srcPort, destSwitchId, destPort);
    }

    private Optional<Isl> getForwardIslOfSegmentData(PathSegmentValidationData data, RepositoryData repositoryData) {
        return repositoryData.getIslRepository().findByEndpoints(
                data.getSrcSwitchId(), data.getSrcPort(),
                data.getDestSwitchId(), data.getDestPort());
    }

    private Optional<Isl> getReverseIslOfSegmentData(PathSegmentValidationData data, RepositoryData repositoryData) {
        return repositoryData.getIslRepository().findByEndpoints(
                data.getDestSwitchId(), data.getDestPort(),
                data.getSrcSwitchId(), data.getSrcPort());
    }

    private Set<String> validateBandwidth(InputData inputData, RepositoryData repositoryData) {
        Optional<Isl> forward = getForwardIslOfSegmentData(inputData.getSegment(), repositoryData);
        Optional<Isl> reverse = getReverseIslOfSegmentData(inputData.getSegment(), repositoryData);

        Set<String> errors = Sets.newHashSet();
        if (!forward.isPresent()) {
            errors.add(getNoForwardIslError(inputData));
        }
        if (!reverse.isPresent()) {
            errors.add(getNoForwardIslError(inputData));
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        if (getForwardBandwidthWithReusableResources(forward.get(), inputData) < inputData.getPath().getBandwidth()) {
            errors.add(getForwardBandwidthErrorMessage(inputData, forward.get().getAvailableBandwidth()));
        }
        if (getReverseBandwidthWithReusableResources(reverse.get(), inputData) < inputData.getPath().getBandwidth()) {
            errors.add(getReverseBandwidthErrorMessage(inputData, reverse.get().getAvailableBandwidth()));
        }

        return errors;
    }

    private boolean isSameForwardPathSegment(PathSegmentValidationData segmentData, PathSegment pathSegment) {
        return segmentData.getSrcSwitchId().equals(pathSegment.getSrcSwitchId())
                && segmentData.getDestSwitchId().equals(pathSegment.getDestSwitchId())
                && segmentData.getSrcPort().equals(pathSegment.getSrcPort())
                && segmentData.getDestPort().equals(pathSegment.getDestPort());
    }

    private boolean isSameReversePathSegment(PathSegmentValidationData segmentData, PathSegment pathSegment) {
        return segmentData.getSrcSwitchId().equals(pathSegment.getDestSwitchId())
                && segmentData.getDestSwitchId().equals(pathSegment.getSrcSwitchId())
                && segmentData.getSrcPort().equals(pathSegment.getDestPort())
                && segmentData.getDestPort().equals(pathSegment.getSrcPort());
    }

    private long getForwardBandwidthWithReusableResources(Isl isl, InputData inputData) {
        return getBandwidthWithReusableResources(inputData, isl,
                pathSegment -> isSameForwardPathSegment(inputData.getSegment(), pathSegment));
    }

    private long getReverseBandwidthWithReusableResources(Isl isl, InputData inputData) {
        return getBandwidthWithReusableResources(inputData, isl,
                pathSegment -> isSameReversePathSegment(inputData.getSegment(), pathSegment));
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
        Set<String> errors = Sets.newHashSet();
        Map<SwitchId, SwitchProperties> switchPropertiesMap = switchPropertiesRepository.findBySwitchIds(
                Sets.newHashSet(inputData.getPath().getSrcSwitchId(), inputData.getPath().getDestSwitchId()));

        if (!switchPropertiesMap.containsKey(inputData.getPath().getSrcSwitchId())) {
            errors.add(getSrcSwitchNotFoundError(inputData));
        } else {
            if (!switchPropertiesMap.get(inputData.getPath().getSrcSwitchId()).getSupportedTransitEncapsulation()
                    .contains(inputData.getPath().getFlowEncapsulationType())) {
                errors.add(getSrcSwitchDoesNotSupportEncapsulationTypeError(inputData));
            }
        }

        if (!switchPropertiesMap.containsKey(inputData.getPath().getDestSwitchId())) {
            errors.add(getDestSwitchNotFoundError(inputData));
        } else {
            if (!switchPropertiesMap.get(inputData.getPath().getDestSwitchId()).getSupportedTransitEncapsulation()
                    .contains(inputData.getPath().getFlowEncapsulationType())) {
                errors.add(getDestSwitchDoesNotSupportEncapsulationTypeError(inputData));
            }
        }

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

    private Set<String> validateLatencyTier2(InputData inputData, RepositoryData repositoryData) {
        return validateLatency(inputData, repositoryData, inputData.getPath()::getLatencyTier2, LATENCY_TIER_2);
    }

    private Set<String> validateLatency(InputData inputData, RepositoryData repositoryData) {
        return validateLatency(inputData, repositoryData, inputData.getPath()::getLatency, LATENCY);
    }

    private Set<String> validateLatency(InputData inputData, RepositoryData repositoryData,
                                        Supplier<Duration> inputLatency, String latencyType) {
        Optional<Isl> forward = getForwardIslOfSegmentData(inputData.getSegment(), repositoryData);
        Optional<Isl> reverse = getReverseIslOfSegmentData(inputData.getSegment(), repositoryData);

        Set<String> errors = Sets.newHashSet();
        if (!forward.isPresent()) {
            errors.add(getNoForwardIslError(inputData));
        }
        if (!reverse.isPresent()) {
            errors.add(getNoReverseIslError(inputData));
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        Duration actualLatency = Duration.ofNanos(forward.get().getLatency());
        if (actualLatency.compareTo(inputLatency.get()) > 0) {
            errors.add(getForwardLatencyErrorMessage(inputData, inputLatency.get(), latencyType, actualLatency));
        }
        Duration actualReverseLatency = Duration.ofNanos(reverse.get().getLatency());
        if (actualReverseLatency.compareTo(inputLatency.get()) > 0) {
            errors.add(getReverseLatencyErrorMessage(inputData, inputLatency.get(), latencyType, actualReverseLatency));
        }
        return errors;
    }

    private String getForwardLatencyErrorMessage(InputData data, Duration expectedLatency, String latencyType,
                                                 Duration actualLatency) {
        return String.format(
                "Requested %s is too low between end points: switch %s port %d and switch"
                        + " %s port %d. Requested %d ms, but the link supports %d ms",
                latencyType,
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                expectedLatency.toMillis(), actualLatency.toMillis());
    }

    private String getReverseLatencyErrorMessage(InputData data, Duration expectedLatency, String latencyType,
                                                 Duration actualLatency) {
        return String.format(
                "Requested %s is too low between end points: switch %s port %d and switch"
                        + " %s port %d. Requested %d ms, but the link supports %d ms",
                latencyType,
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                expectedLatency.toMillis(), actualLatency.toMillis());
    }

    private String getForwardBandwidthErrorMessage(InputData data, long actualBandwidth) {
        return String.format(
                "There is not enough bandwidth between end points: switch %s port %d and switch %s port %d"
                        + " (forward path). Requested bandwidth %d, but the link supports %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getPath().getBandwidth(), actualBandwidth);
    }

    private String getReverseBandwidthErrorMessage(InputData data, long actualBandwidth) {
        return String.format(
                "There is not enough bandwidth between end points: switch %s port %d and switch %s port %d"
                        + " (reverse path). Requested bandwidth %d, but the link supports %d",
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getPath().getBandwidth(), actualBandwidth);
    }

    private String getNoForwardIslError(InputData data) {
        return String.format(
                "There is no ISL between end points: switch %s port %d and switch %s port %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort());
    }

    private String getNoReverseIslError(InputData data) {
        return String.format(
                "There is no ISL between end points: switch %s port %d and switch %s port %d",
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort(),
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort());
    }

    private String getForwardIslNotActiveError(InputData data) {
        return String.format(
                "The ISL is not in ACTIVE state between end points: switch %s port %d and switch %s port %d",
                data.getSegment().getSrcSwitchId(), data.getSegment().getSrcPort(),
                data.getSegment().getDestSwitchId(), data.getSegment().getDestPort());
    }

    private String getReverseIslNotActiveError(InputData data) {
        return String.format(
                "The ISL is not in ACTIVE state between end points: switch %s port %d and switch %s port %d",
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
        return String.format("The switch %s doesn't support the encapsulation type %s",
                data.getSegment().getSrcSwitchId(), data.getPath().getFlowEncapsulationType());
    }

    private String getDestSwitchDoesNotSupportEncapsulationTypeError(InputData data) {
        return String.format("The switch %s doesn't support the encapsulation type %s",
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
        SwitchRepository switchRepository;
    }
}
