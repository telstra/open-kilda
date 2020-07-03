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

import lombok.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OneToOneMapping {
    private final Clock clock;
    private final Duration staleWipeDelay;

    private final Map<SwitchId, String> actual = new HashMap<>();
    private final Map<SwitchId, StaleEntry> removed = new HashMap<>();

    public OneToOneMapping(Clock clock, Duration staleWipeDelay) {
        this.clock = clock;
        this.staleWipeDelay = staleWipeDelay;
    }

    /**
     * Lookup entry.
     */
    public Optional<String> lookup(SwitchId switchId, boolean allowStale) {
        flushStale();
        String result = actual.get(switchId);
        if (result != null) {
            return Optional.of(result);
        }
        if (allowStale) {
            return Optional.ofNullable(lookupStale(switchId));
        }
        return Optional.empty();
    }

    /**
     * Add mapping entry.
     */
    public void add(SwitchId switchId, String region) {
        actual.put(switchId, region);
        removed.remove(switchId);
    }

    /**
     * Remove(make stale) mapping entry.
     */
    public void remove(SwitchId switchId) {
        String region = actual.remove(switchId);
        if (region != null && ! staleWipeDelay.isZero()) {
            StaleEntry entry = new StaleEntry(region, clock.instant().plus(staleWipeDelay));
            removed.put(switchId, entry);
        }
    }

    /**
     * Make reversed mapping.
     */
    public Map<String, Set<SwitchId>> makeReversedMapping() {
        Map<String, Set<SwitchId>> reversed = new HashMap<>();
        for (Map.Entry<SwitchId, String> entry : actual.entrySet()) {
            reversed.computeIfAbsent(entry.getValue(), key -> new HashSet<>())
                    .add(entry.getKey());
        }
        return reversed;
    }

    private String lookupStale(SwitchId switchId) {
        StaleEntry entry = removed.get(switchId);
        if (entry == null) {
            return null;
        }

        Instant now = clock.instant();
        if (entry.expireAt.isBefore(now)) {
            return null;
        }
        removed.put(switchId, new StaleEntry(entry.getRegion(), now.plus(staleWipeDelay)));
        return entry.getRegion();
    }

    private void flushStale() {
        Instant now = clock.instant();
        removed.entrySet()
                .removeIf(keyValue -> keyValue.getValue().getExpireAt().isBefore(now));
    }

    @Value
    private static class StaleEntry {
        private String region;
        private Instant expireAt;
    }
}
