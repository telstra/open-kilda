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
import org.openkilda.wfm.error.NameCollisionException;

/**
 * Represents topology interface.
 */
public interface Topology {
    /**
     * Default topologies configuration file.
     */
    String TOPOLOGY_PROPERTIES = "/topology.properties";
    String TOPOLOGY_PROPERTIES_DEFAULTS_PREFIX = "defaults.";

    String getTopologyName();

    /**
     * Topology creator.
     * Should be implemented.
     *
     * @return {@link StormTopology}
     */
    StormTopology createTopology() throws NameCollisionException;

    /**
     * Returns topology name.
     * Default implementation.
     *
     * @return topology name
     */
    default String makeTopologyName() {
        return getClass().getSimpleName().toLowerCase();
    }
}
