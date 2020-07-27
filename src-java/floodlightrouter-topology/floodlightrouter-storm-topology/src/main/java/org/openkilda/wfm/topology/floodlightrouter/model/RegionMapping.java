/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.model;

import org.openkilda.model.SwitchId;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RegionMapping implements RegionMappingStorage {
    private final OneToOneMapping readWrite;
    private final OneToManyMapping readOnly;

    public RegionMapping(Clock clock, Duration staleWipeDelay) {
        readWrite = new OneToOneMapping(clock, staleWipeDelay);
        readOnly = new OneToManyMapping();
    }

    /**
     * Looks for a region for switchId.
     */
    public Optional<String> lookupReadWriteRegion(SwitchId switchId) {
        return readWrite.lookup(switchId);
    }

    public Map<String, Set<SwitchId>> organizeReadWritePopulationPerRegion() {
        return readWrite.makeReversedMapping();
    }

    public Map<String, Set<SwitchId>> organizeReadOnlyPopulationPerRegion() {
        return readOnly.makeReversedMapping();
    }

    /**
     * Updates region mapping for switch.
     */
    public void apply(RegionMappingUpdate update) {
        update.apply(this);
    }

    @Override
    public void update(RegionMappingAdd action) {
        selectUpdateTarget(action).add(action.getSwitchId(), action.getRegion());
    }

    @Override
    public void update(RegionMappingRemove action) {
        selectUpdateTarget(action).remove(action.getSwitchId(), action.getRegion());
    }

    @Override
    public void update(RegionMappingSet action) {
        selectUpdateTarget(action).set(action.getSwitchId(), action.getRegion());
    }

    private MappingApproach selectUpdateTarget(RegionMappingUpdate update) {
        return update.isReadWriteMode() ? readWrite : readOnly;
    }
}
