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

package org.openkilda.pce;

import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.persistence.repositories.FlowPathRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is responsible for setting additional weights on flow paths. This class is used for preparing an available
 * network object for further processing such as finding diverse or protected paths.
 */
@Slf4j
public final class DiverseWeightsProcessor {

    private DiverseWeightsProcessor() {
    }

    /**
     * This method mutates the network by adding diverse weights if it is required based on the provided parameters.
     * @param parameters parameters of the flow for which the available network is used.
     * @param reusePathsResources treat these resources as available
     * @param network mutable available network that will be used for operations on the flow
     * @param flowPathRepository flow path repository
     */
    public static void fillDiverseWeights(FlowParameters parameters,
                                          Collection<PathId> reusePathsResources,
                                          AvailableNetwork network,
                                          FlowPathRepository flowPathRepository) {
        Set<PathId> flowPathIds = new HashSet<>(flowPathRepository.findPathIdsByFlowDiverseGroupId(
                parameters.getDiverseGroupId()));
        if (!reusePathsResources.isEmpty()) {
            flowPathIds = flowPathIds.stream()
                    .filter(s -> !reusePathsResources.contains(s))
                    .collect(Collectors.toSet());
        }

        Set<PathId> affinityPathIds =
                new HashSet<>(flowPathRepository.findPathIdsByFlowAffinityGroupId(parameters.getAffinityGroupId()));

        List<FlowPath> flowPathsToProcess = flowPathIds.stream()
                .map(flowPathRepository::findById)
                // TODO replace it with Optional::stream once JDK9 is available
                .flatMap(optional -> optional.map(Stream::of).orElseGet(Stream::empty))
                .filter(flowPath -> isNeedToAddDiversePenalties(flowPath, affinityPathIds, parameters))
                .collect(Collectors.toList());

        processDiversityPenalties(parameters, network, flowPathsToProcess);
    }

    /**
     * This method mutates the provided network by setting penalties on diverse on the provided diverse paths.
     * This is needed when calculating a protected path that must be diverse from the main path.
     * @param parameters parameters of the flow for which the available network is used
     * @param network mutable available network that will be used for operations on the flow
     * @param diversePaths a list of paths that must have an increased diversity weight
     */
    public static void processDiversityPenalties(FlowParameters parameters, AvailableNetwork network,
                                                 List<FlowPath> diversePaths) {
        log.info("processDiversityPenalties start processing for: {}", diversePaths);

        diversePaths.forEach(flowPath -> {
            network.processDiversityGroupForSingleSwitchFlow(flowPath);
            network.processDiversitySegments(flowPath.getSegments(),
                    parameters.getTerminatingSwitchIds());
            network.processDiversitySegmentsWithPop(flowPath.getSegments());
        });
    }

    private static boolean isNeedToAddDiversePenalties(FlowPath path, Set<PathId> affinityPathIds,
                                                FlowParameters parameters) {
        if (parameters.isCommonFlow() && Objects.equals(path.getFlowId(), parameters.getFlowId())) {
            return true; // it is a diverse group for a protected path of a common flow
        }
        if (parameters.isHaFlow() && Objects.equals(path.getHaFlowId(), parameters.getHaFlowId())) {
            return true; // it is a diverse group for a protected path of an HA-flow
        }
        return !affinityPathIds.contains(path.getPathId()); // affinity group priority is higher then diversity
    }
}
