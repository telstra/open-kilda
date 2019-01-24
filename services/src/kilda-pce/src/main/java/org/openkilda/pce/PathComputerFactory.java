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
import org.openkilda.pce.impl.InMemoryPathComputer;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.annotations.VisibleForTesting;

/**
 * A factory for {@link PathComputer} instances. It provides a specific {@link PathComputer} depending on configuration
 * ({@link PathComputerConfig}) and requested strategy ({@link WeightStrategy}).
 */
public class PathComputerFactory {

    private PathComputerConfig config;
    private AvailableNetworkFactory availableNetworkFactory;

    public PathComputerFactory(PathComputerConfig config, AvailableNetworkFactory availableNetworkFactory) {
        this.config = config;
        this.availableNetworkFactory = availableNetworkFactory;
    }

    /**
     * Gets a specific {@link PathComputer} as per the weight strategy.
     *
     * @param weightStrategy the path find weight strategy.
     * @return {@link PathComputer} instances
     */
    public PathComputer getPathComputer(WeightStrategy weightStrategy) {
        return new InMemoryPathComputer(availableNetworkFactory,
                new BestCostAndShortestPathFinder(
                        config.getMaxAllowedDepth(),
                        getWeightFunctionByStrategy(weightStrategy)));
    }

    /**
     * Gets a specific {@link PathComputer} with default (configurable) strategy.
     *
     * @return {@link PathComputer} instances
     */
    public PathComputer getPathComputer() {
        return getPathComputer(WeightStrategy.from(config.getStrategy()));
    }

    /**
     * Returns weight computing function for passed strategy.
     *
     * @param strategy the weight computation strategy
     * @return weight computing function.
     */
    @VisibleForTesting
    public WeightFunction getWeightFunctionByStrategy(WeightStrategy strategy) {
        switch (strategy) { //NOSONAR
            case COST:
                return edge ->
                        (long) (edge.getCost() == 0 ? config.getDefaultIslCost() : edge.getCost());
            default:
                throw new UnsupportedOperationException(String.format("Unsupported strategy type %s", strategy));
        }
    }

    /**
     * BuildStrategy is used for getting a PathComputer instance  - ie what filters to apply. In reality, to provide
     * flexibility, this should most likely be one or more strings.
     */
    public enum WeightStrategy {
        HOPS,

        /**
         * BuildStrategy based on cost of links.
         */
        COST,

        LATENCY, EXTERNAL;

        private static WeightStrategy from(String strategy) {
            try {
                return valueOf(strategy.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("BuildStrategy %s is not supported", strategy));
            }
        }
    }
}
