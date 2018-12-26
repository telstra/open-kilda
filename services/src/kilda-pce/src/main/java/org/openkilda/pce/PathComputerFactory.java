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

import org.openkilda.pce.finder.BestCostAndShortestPathFinder;
import org.openkilda.pce.finder.BestCostSymmetricPathFinder;
import org.openkilda.pce.impl.InMemoryPathComputer;
import org.openkilda.pce.impl.SymmetricPathComputer;
import org.openkilda.persistence.repositories.RepositoryFactory;

import java.util.Arrays;

/**
 * A factory for {@link PathComputer} instances. It provides a specific {@link PathComputer} depending on configuration
 * ({@link PathComputerConfig}) and requested strategy ({@link Strategy}).
 */
public class PathComputerFactory {

    private PathComputerConfig config;
    private RepositoryFactory repositoryFactory;

    public PathComputerFactory(PathComputerConfig config, RepositoryFactory repositoryFactory) {
        this.config = config;
        this.repositoryFactory = repositoryFactory;
    }

    /**
     * Gets a specific {@link PathComputer} as per the strategy.
     *
     * @param strategy the path find strategy.
     * @return {@link PathComputer} instances
     */
    public PathComputer getPathComputer(Strategy strategy) {
        if (strategy == Strategy.COST) {
            return new InMemoryPathComputer(repositoryFactory.createIslRepository(),
                    new BestCostAndShortestPathFinder(config.getMaxAllowedDepth(), config.getDefaultIslCost()));
        } else if (strategy == Strategy.SYM_COST) {
            return new SymmetricPathComputer(repositoryFactory.createIslRepository(),
                    new BestCostSymmetricPathFinder(config.getDefaultIslCost()));
        } else {
            throw new UnsupportedOperationException(String.format("Unsupported strategy type %s", strategy));
        }
    }

    /**
     * Gets a specific {@link PathComputer} with default (configurable) strategy.
     *
     * @return {@link PathComputer} instances
     */
    public PathComputer getPathComputer() {
        return getPathComputer(Strategy.from(config.getStrategy()));
    }

    /**
     * Strategy is used for getting a PathComputer instance  - ie what filters to apply. In reality, to provide
     * flexibility, this should most likely be one or more strings.
     */
    public enum Strategy {
        HOPS,

        /**
         * Strategy based on cost of links.
         */
        COST,

        /**
         * Based on cost with always equal forward and reverse paths.
         */
        SYM_COST, LATENCY, EXTERNAL;

        private static Strategy from(String strategy) {
            return Arrays.stream(values())
                    .filter(item -> item.name().equalsIgnoreCase(strategy))
                    .findFirst()
                    .orElse(COST);
        }
    }
}
