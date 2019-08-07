/* Copyright 2018 Telstra Open Source
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

import org.openkilda.model.Flow;
import org.openkilda.model.Isl;
import org.openkilda.model.IslConfig;
import org.openkilda.model.PathId;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A factory for {@link AvailableNetwork} instances.
 */
@Slf4j
public class AvailableNetworkFactory {
    private PathComputerConfig config;
    private IslRepository islRepository;
    private FlowPathRepository flowPathRepository;

    public AvailableNetworkFactory(PathComputerConfig config, RepositoryFactory repositoryFactory) {
        this.config = config;
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
    }

    /**
     * Gets a {@link AvailableNetwork}, built with specified strategy.
     *
     * @param flow                        the flow, for which {@link AvailableNetwork} is constructing.
     * @param reuseResourcesForPaths      reuse resources already allocated by {@param reuseResourcesForPaths} paths.
     * @return {@link AvailableNetwork} instance.
     */
    public AvailableNetwork getAvailableNetwork(Flow flow, List<PathId> reuseResourcesForPaths, IslConfig islConfig)
            throws RecoverableException {
        return getAvailableNetwork(flow, reuseResourcesForPaths, BuildStrategy.from(config.getNetworkStrategy()),
                islConfig);
    }

    /**
     * Gets a {@link AvailableNetwork}, built with specified buildStrategy.
     *
     * @param flow                        the flow, for which {@link AvailableNetwork} is constructing.
     * @param reusePathsResources         reuse resources already allocated by paths.
     * @param buildStrategy               the {@link AvailableNetwork} building buildStrategy.
     * @return {@link AvailableNetwork} instance
     */
    public AvailableNetwork getAvailableNetwork(Flow flow, List<PathId> reusePathsResources,
                                                BuildStrategy buildStrategy,
                                                IslConfig islConfig) throws RecoverableException {
        AvailableNetwork network = new AvailableNetwork();
        try {
            // Reads all active links from the database and creates representation of the network.
            Collection<Isl> links = getAvailableIsls(buildStrategy, flow);
            links.forEach(isl -> network.addLink(isl, islConfig));

            if (!reusePathsResources.isEmpty() && !flow.isIgnoreBandwidth()) {
                // ISLs occupied by the flow (take the bandwidth already occupied by the flow into account).
                Collection<Isl> flowLinks = islRepository.findActiveAndOccupiedByFlowPathWithAvailableBandwidth(
                        reusePathsResources, flow.getBandwidth(), flow.getEncapsulationType());
                flowLinks.forEach(isl -> network.addLink(isl, islConfig));
            }
        } catch (PersistenceException e) {
            throw new RecoverableException("An error from neo4j", e);
        }

        if (flow.getGroupId() != null) {
            log.info("Filling AvailableNetwork diverse weighs for group with id {}", flow.getGroupId());

            Collection<PathId> flowPaths = flowPathRepository.findPathIdsByFlowGroupId(flow.getGroupId());
            if (!reusePathsResources.isEmpty()) {
                flowPaths = flowPaths.stream()
                        .filter(s -> !reusePathsResources.contains(s))
                        .collect(Collectors.toList());
            }

            flowPaths.forEach(pathId ->
                    flowPathRepository.findById(pathId)
                            .ifPresent(flowPath ->
                                    network.processDiversitySegments(flowPath.getSegments(), config)));
        }

        return network;
    }

    private Collection<Isl> getAvailableIsls(BuildStrategy buildStrategy, Flow flow) {
        if (buildStrategy == BuildStrategy.COST) {
            return flow.isIgnoreBandwidth() ? islRepository.findAllActive() :
                    islRepository.findActiveWithAvailableBandwidth(flow.getBandwidth(), flow.getEncapsulationType());
        } else if (buildStrategy == BuildStrategy.SYMMETRIC_COST) {
            return flow.isIgnoreBandwidth() ? islRepository.findAllActive() :
                    islRepository.findSymmetricActiveWithAvailableBandwidth(flow.getBandwidth(),
                            flow.getEncapsulationType());
        } else {
            throw new UnsupportedOperationException(String.format("Unsupported buildStrategy type %s", buildStrategy));
        }
    }

    public enum BuildStrategy {
        /**
         * WeightStrategy based on cost of links.
         */
        COST,

        /**
         * Based on cost with always equal forward and reverse paths.
         */
        SYMMETRIC_COST;

        private static BuildStrategy from(String strategy) {
            try {
                return valueOf(strategy.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("BuildStrategy %s is not supported", strategy));
            }
        }
    }
}
