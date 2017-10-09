/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology;

import org.apache.storm.generated.StormTopology;

/**
 * Represents topology interface.
 */
public interface Topology {
    /**
     * Zookeeper hosts property name.
     */
    String PROPERTY_ZOOKEEPER = "zookeeper.hosts";

    /**
     * Kafka hosts property name.
     */
    String PROPERTY_KAFKA = "kafka.hosts";

    /**
     * Parallelism value property name.
     */
    String PROPERTY_PARALLELISM = "parallelism";

    /**
     * Workers value property name.
     */
    String PROPERTY_WORKERS = "workers";

    /**
     * Default topologies configuration file.
     */
    String TOPOLOGY_PROPERTIES = "/topology.properties";

    /**
     * Topology creator.
     * Should be implemented.
     *
     * @return {@link StormTopology}
     */
    StormTopology createTopology();

    /**
     * Returns topology name.
     * Default implementation.
     *
     * @return topology name
     */
    default String getTopologyName() {
        return getClass().getSimpleName().toLowerCase();
    }

    /**
     * Returns topology specific property name.
     * Adds topology name to property name.
     * Default implementation.
     *
     * @param propertyName property name
     * @return topology specific property name
     */
    default String getTopologyPropertyName(final String propertyName) {
        return String.format("%s.%s", getTopologyName(), propertyName);
    }
}
