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

package org.openkilda.persistence.repositories;

import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.Optional;

public interface IslRepository extends Repository<Isl> {
    Collection<Isl> findBySrcEndpoint(SwitchId srcSwitchId, int srcPort);

    Collection<Isl> findByDestEndpoint(SwitchId dstSwitchId, int dstPort);

    Optional<Isl> findByEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort);

    /**
     * Finds active ISLs for the path occupied by the flow, filtering out ISLs that don't have enough available
     * bandwidth.
     * <p/>
     * ISLs must have available bandwidth to satisfy the difference between newly requested and already taken by the
     * same flow.
     *
     * @param flowId            the flow ID.
     * @param requiredBandwidth required bandwidth amount that should be available on ISLs.
     */
    Collection<Isl> findActiveAndOccupiedByFlowWithAvailableBandwidth(String flowId, long requiredBandwidth);

    /**
     * Finds all active ISLs.
     */
    Collection<Isl> findAllActive();

    /**
     * Finds all active ISLs, filtering out ISLs that don't have enough available bandwidth.
     *
     * @param requiredBandwidth required bandwidth amount that should be available on ISLs.
     */
    Collection<Isl> findActiveWithAvailableBandwidth(long requiredBandwidth);

    /**
     * Finds all active ISLs, ignores ISLs if they have not enough bandwidth in any direction.
     * @param requiredBandwidth required available bandwidth amount.
     * @return list of ISLs.
     */
    Collection<Isl> findSymmetricActiveWithAvailableBandwidth(long requiredBandwidth);
}
