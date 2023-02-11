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

import org.openkilda.messaging.info.network.PathValidateData;
import org.openkilda.model.Isl;
import org.openkilda.pce.Path;
import org.openkilda.persistence.repositories.IslRepository;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


public class PathValidator {

    private final IslRepository islRepository;

    public PathValidator(IslRepository islRepository) {
        this.islRepository = islRepository;
    }

    /**
     * Validates whether it is possible to create a path with the given parameters. When there obstacles, this validator
     * returns all errors found on each segment. For example, when there is a path 1-2-3-4 and there is no link between
     * 1 and 2, no sufficient latency between 2 and 3, and not enough bandwidth between 3-4, then this validator returns
     * all 3 errors.
     * @param path path parameters to validate.
     * @return a response object containing the validation result and errors if any
     */
    public PathValidateData validatePath(Path path) {
        Set<String> result = path.getSegments().stream()
                .map(segment -> executeValidations(new PathSegment(path, segment), islRepository, getValidations(path)))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        return PathValidateData.builder()
                .isValid(result.isEmpty())
                .errors(new LinkedList<>(result))
                .build();
    }

    private Set<String> executeValidations(PathSegment segment, IslRepository islRepository,
            List<BiFunction<PathSegment, IslRepository, Set<String>>> validations) {

        return validations.stream()
                .map(f -> f.apply(segment, islRepository))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private List<BiFunction<PathSegment, IslRepository, Set<String>>> getValidations(Path path) {
        List<BiFunction<PathSegment, IslRepository, Set<String>>> validationFunctions = new LinkedList<>();

        validationFunctions.add(getValidateLinkBiFunction());

        if (path.getLatency() != 0) {
            validationFunctions.add(getValidateLatencyFunction());
        }
        if (path.getMinAvailableBandwidth() != 0) {
            validationFunctions.add(getValidateBandwidthFunction());
        }
        return validationFunctions;
    }

    private BiFunction<PathSegment, IslRepository, Set<String>> getValidateLinkBiFunction() {
        return (pathSegment, islRepository) -> {

            if (!islRepository.findByEndpoints(pathSegment.getSegment().getSrcSwitchId(),
                    pathSegment.getSegment().getSrcPort(),
                    pathSegment.getSegment().getDestSwitchId(),
                    pathSegment.getSegment().getDestPort()).isPresent()) {
                return Collections.singleton(getNoLinkError(pathSegment));
            }

            return Collections.emptySet();
        };
    }

    private BiFunction<PathSegment, IslRepository, Set<String>> getValidateBandwidthFunction() {
        return (pathSegment, islRepository) -> {
            Optional<Isl> isl = islRepository.findByEndpoints(
                    pathSegment.getSegment().getSrcSwitchId(), pathSegment.getSegment().getSrcPort(),
                    pathSegment.getSegment().getDestSwitchId(), pathSegment.getSegment().getDestPort());
            if (!isl.isPresent()) {
                return Collections.singleton(getNoLinkError(pathSegment));
            }

            if (isl.get().getAvailableBandwidth() < pathSegment.path.getMinAvailableBandwidth()) {
                return Collections.singleton(getBandwidthErrorMessage(pathSegment,
                        isl.get().getAvailableBandwidth(), pathSegment.path.getMinAvailableBandwidth()));
            }

            return Collections.emptySet();
        };
    }

    private BiFunction<PathSegment, IslRepository, Set<String>> getValidateLatencyFunction() {
        return (pathSegment, islRepository) -> {
            Optional<Isl> isl = islRepository.findByEndpoints(
                    pathSegment.getSegment().getSrcSwitchId(), pathSegment.getSegment().getSrcPort(),
                    pathSegment.getSegment().getDestSwitchId(), pathSegment.getSegment().getDestPort());
            if (!isl.isPresent()) {
                return Collections.singleton(getNoLinkError(pathSegment));
            }

            if (isl.get().getLatency() > pathSegment.getPath().getLatency()) {
                return Collections.singleton(getLatencyErrorMessage(pathSegment,
                        pathSegment.getPath().getLatency(), isl.get().getLatency()));
            }

            return Collections.emptySet();
        };
    }

    private String getLatencyErrorMessage(PathSegment pathSegment, long requestedLatency, long actualLatency) {
        return String.format(
                "Requested latency is too low between source switch %s port %d and destination switch %s port %d."
                + " Requested %d, but the link supports %d",
                pathSegment.getSegment().getSrcSwitchId(), pathSegment.getSegment().getSrcPort(),
                pathSegment.getSegment().getDestSwitchId(), pathSegment.getSegment().getDestPort(),
                requestedLatency, actualLatency);
    }

    private String getBandwidthErrorMessage(PathSegment pathSegment, Long requestedBandwidth, long actualBandwidth) {
        return String.format(
                "There is not enough Bandwidth between source switch %s port %d and destination switch %s port %d."
                + " Requested bandwidth %d, but the link supports %d",
                pathSegment.getSegment().getSrcSwitchId(), pathSegment.getSegment().getSrcPort(),
                pathSegment.getSegment().getDestSwitchId(), pathSegment.getSegment().getDestPort(),
                requestedBandwidth, actualBandwidth);
    }

    private String getNoLinkError(PathSegment pathSegment) {
        return String.format(
                "There is no ISL between source switch %s port %d and destination switch %s port %d",
                pathSegment.getSegment().getSrcSwitchId(), pathSegment.getSegment().getSrcPort(),
                pathSegment.getSegment().getDestSwitchId(), pathSegment.getSegment().getDestPort());
    }

    @Getter
    @AllArgsConstructor
    private static class PathSegment {
        Path path;
        Path.Segment segment;
    }
}
