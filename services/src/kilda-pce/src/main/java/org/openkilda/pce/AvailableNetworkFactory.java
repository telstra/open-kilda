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
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import java.util.Collection;

/**
 * A factory for {@link AvailableNetwork} instances.
 */
public class AvailableNetworkFactory {
    private PathComputerConfig config;
    private IslRepository islRepository;

    public AvailableNetworkFactory(PathComputerConfig config, RepositoryFactory repositoryFactory) {
        this.config = config;
        this.islRepository = repositoryFactory.createIslRepository();
    }

    /**
     * Gets a {@link AvailableNetwork}, built with specified strategy.
     *
     * @return {@link AvailableNetwork} instance
     */
    public AvailableNetwork getAvailableNetwork(Flow flow, boolean reuseAllocatedFlowBandwidth)
            throws RecoverableException {
        return getAvailableNetwork(flow, reuseAllocatedFlowBandwidth, BuildStrategy.from(config.getNetworkStrategy()));
    }

    /**
     * Gets a {@link AvailableNetwork}, built with specified buildStrategy.
     *
     * @param buildStrategy the {@link AvailableNetwork} building buildStrategy.
     * @return {@link AvailableNetwork} instance
     */
    public AvailableNetwork getAvailableNetwork(
            Flow flow, boolean reuseAllocatedFlowBandwidth, BuildStrategy buildStrategy) throws RecoverableException {
        AvailableNetwork network = new AvailableNetwork();
        try {
            // Reads all active links from the database and creates representation of the network.
            Collection<Isl> links = getAvailableIsls(buildStrategy, flow);
            links.forEach(network::addLink);

            if (reuseAllocatedFlowBandwidth && !flow.isIgnoreBandwidth()) {
                // ISLs occupied by the flow (take the bandwidth already occupied by the flow into account).
                Collection<Isl> flowLinks = islRepository.findActiveAndOccupiedByFlowWithAvailableBandwidth(
                        flow.getFlowId(), flow.getBandwidth());
                flowLinks.forEach(network::addLink);
            }
        } catch (PersistenceException e) {
            throw new RecoverableException("An error from neo4j", e);
        }

        return network;
    }

    private Collection<Isl> getAvailableIsls(BuildStrategy buildStrategy, Flow flow) {
        if (buildStrategy == BuildStrategy.COST) {
            return flow.isIgnoreBandwidth() ? islRepository.findAllActive() :
                    islRepository.findActiveWithAvailableBandwidth(flow.getBandwidth());
        } else if (buildStrategy == BuildStrategy.SYMMETRIC_COST) {
            return flow.isIgnoreBandwidth() ? islRepository.findAllActive() :
                    islRepository.findSymmetricActiveWithAvailableBandwidth(flow.getBandwidth());
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
