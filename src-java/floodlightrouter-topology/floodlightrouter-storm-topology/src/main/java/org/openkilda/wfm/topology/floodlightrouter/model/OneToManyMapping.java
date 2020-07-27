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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class OneToManyMapping implements MappingApproach {
    private final Map<SwitchId, Set<String>> mapping = new HashMap<>();

    @Override
    public void set(SwitchId switchId, String region) {
        remove(switchId, region);
        if (region != null) {
            add(switchId, region);
        }
    }

    public void add(SwitchId switchId, String region) {
        mapping.computeIfAbsent(switchId, key -> new HashSet<>())
                .add(region);
    }

    public void remove(SwitchId switchId, String region) {
        if (region == null) {
            mapping.remove(switchId);
        } else {
            Set<String> entry = mapping.get(switchId);
            entry.remove(region);
            if (entry.isEmpty()) {
                mapping.remove(switchId);
            }
        }
    }

    public Map<String, Set<SwitchId>> makeReversedMapping() {
        Map<String, Set<SwitchId>> result = new HashMap<>();
        for (Map.Entry<SwitchId, Set<String>> entry : mapping.entrySet()) {
            for (String region : entry.getValue()) {
                SwitchId switchId = entry.getKey();
                result.computeIfAbsent(region, key -> new HashSet<>())
                        .add(switchId);
            }
        }
        return result;
    }
}
