/* Copyright 2019 Telstra Open Source
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

package org.openkilda.persistence.repositories;

import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;

import java.util.Collection;
import java.util.Optional;

public interface TransitVlanRepository extends Repository<TransitVlan> {
    Collection<TransitVlan> findByPathId(PathId pathId, PathId oppositePathId);

    /**
     * Find a transit vlan which is not assigned to any flow.
     * Use the provided defaultVlan as the first candidate.
     *
     * @param defaultVlan the potential vlan to be checked first.
     * @return a transit vlan or {@link Optional#empty()} if no vlan available.
     */
    Optional<Integer> findUnassignedTransitVlan(int defaultVlan);
}
