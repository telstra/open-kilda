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

public interface IslRepository extends Repository<Isl> {
    Collection<Isl> findAllOrderedBySrcSwitch();

    Isl findByEndpoint(SwitchId switchId, int port);

    Isl findByEndpoints(SwitchId switchId, int sourcePort, SwitchId destinationPort, int destinationId);

    /**
     * Finds ISLs occupied by the flow, filtering out ISLs that don't have enough available bandwidth.
     *
     * @param flowId            the flow ID.
     * @param ignoreBandwidth   defines whether bandwidth of links should be ignored.
     * @param requiredBandwidth required bandwidth amount that should be available on ISLs.
     */
    Iterable<Isl> findOccupiedByFlow(String flowId, boolean ignoreBandwidth, long requiredBandwidth);

    /**
     * Finds all active ISLs.
     *
     * @param ignoreBandwidth   defines whether bandwidth of links should be ignored.
     * @param requiredBandwidth required bandwidth amount that should be available on ISLs.
     */
    Iterable<Isl> findActiveWithAvailableBandwidth(boolean ignoreBandwidth, long requiredBandwidth);

    /**
     * Delete ISL.
     *
     * @param isl ISL to be deleted
     */
    void delete(Isl isl);
}
