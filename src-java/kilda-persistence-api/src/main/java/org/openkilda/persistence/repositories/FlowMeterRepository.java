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

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;

public interface FlowMeterRepository extends Repository<FlowMeter> {
    Collection<FlowMeter> findAll();

    /**
     * Find meters by Path Id.
     *
     * @param pathId path ID
     * @return a collection of {@link FlowMeter}
     */
    Optional<FlowMeter> findByPathId(PathId pathId);

    /**
     * Find a meter by unique ID.
     */
    Optional<FlowMeter> findById(SwitchId switchId, MeterId meterId);

    boolean exists(SwitchId switchId, MeterId meterId);

    /**
     * Find the first (lowest by value) meter ID which is not assigned to any flow.
     *
     * @param switchId the switch defines where the meter is applied on.
     * @param lowestMeterId the lowest value for a potential meter.
     * @param highestMeterId the highest value for a potential meter.
     * @return the found meter ID
     */
    Optional<MeterId> findFirstUnassignedMeter(SwitchId switchId, MeterId lowestMeterId, MeterId highestMeterId);
}
