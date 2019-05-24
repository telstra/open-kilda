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

package org.openkilda.wfm.topology.floodlightrouter.service;

import org.openkilda.model.SwitchId;

import lombok.Value;

import java.util.HashMap;
import java.util.Map;

@Value
public class SwitchTracker {
    private Map<SwitchId, String> mapping = new HashMap<>();

    /**
     * Looks for a region for switchId.
     * @param switchId - target switch id
     * @return region or null
     */
    public String lookupRegion(SwitchId switchId) {
        return mapping.getOrDefault(switchId, null);
    }

    /**
     * Updates region mapping for switch.
     * @param switchMapping target mapping to update
     */
    public void updateRegion(SwitchMapping switchMapping) {
        mapping.put(switchMapping.getSwitchId(), switchMapping.getRegion());
    }

}
