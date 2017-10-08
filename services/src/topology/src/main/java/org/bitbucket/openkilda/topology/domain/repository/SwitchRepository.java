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

package org.bitbucket.openkilda.topology.domain.repository;

import org.bitbucket.openkilda.topology.domain.Switch;

import org.springframework.data.neo4j.repository.GraphRepository;

/**
 * Switch repository.
 * Manages operations on switches.
 */
public interface SwitchRepository extends GraphRepository<Switch> {
    /**
     * Finds switch by name.
     *
     * @param name switch datapath id
     * @return {@link Switch} instance
     */
    Switch findByName(String name);
}
