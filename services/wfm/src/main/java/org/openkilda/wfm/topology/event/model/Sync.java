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

package org.openkilda.wfm.topology.event.model;

import org.openkilda.model.SwitchId;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode
public class Sync implements Serializable {
    @Getter
    private final HashMap<SwitchId, Set<Integer>> activePorts = new HashMap<>();

    public void addActiveSwitch(SwitchId switchId) {
        // at this moment only ports are used in sync process
    }

    public void addActivePort(SwitchId switchId, int portNumber) {
        activePorts.computeIfAbsent(switchId, key -> new HashSet<>()).add(portNumber);
    }
}
