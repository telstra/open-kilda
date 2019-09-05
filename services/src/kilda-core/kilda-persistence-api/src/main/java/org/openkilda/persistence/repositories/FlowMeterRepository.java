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
    Optional<FlowMeter> findLldpMeterByMeterIdSwitchIdAndFlowId(MeterId meterId, SwitchId switchId, String flowId);

    /**
     * Find meters by Path Id.
     * Two types on meters can be assigned to one path: ingress meter and LLDP meter.
     *
     * @param pathId path ID
     * @return a collection of {@link FlowMeter}
     */
    Collection<FlowMeter> findByPathId(PathId pathId);

    /**
     * Find a meter id which is not assigned to any flow.
     * Use the provided {@code defaultMeterId} as the first candidate.
     *
     * @param switchId       the switch defines where the meter is applied on.
     * @param defaultMeterId the potential meter to be checked first.
     * @return a meter id or {@link Optional#empty()} if no meter available.
     */
    Optional<MeterId> findUnassignedMeterId(SwitchId switchId, MeterId defaultMeterId);
}
