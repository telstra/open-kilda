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

package org.openkilda.topology.service;

import org.openkilda.topology.model.Topology;

/**
 * Topology service interface.
 */
public interface TopologyService {
    /**
     * Cleans topology.
     *
     * @param correlationId request correlation id
     * @return network topology
     */
    Topology clear(final String correlationId);

    /**
     * Dumps topology.
     *
     * @param correlationId request correlation id
     * @return network topology
     */
    Topology network(final String correlationId);
}
