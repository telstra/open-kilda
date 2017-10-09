/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.utils;

import org.openkilda.messaging.info.event.PathNode;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by carmine on 5/14/17.
 */
public class LinkTracker implements Serializable {

    /**
     * SwitchID -> PortID, Transmitted Frames
     */
    protected ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> state = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, AtomicInteger> getSwitchPorts(String switchID) {
        return state.get(switchID);
    }

    public ConcurrentHashMap<String, AtomicInteger> getOrNewSwitchPorts(String switchID) {
        return state.computeIfAbsent(switchID, k -> new ConcurrentHashMap<>());
    }

    /** for use in foreach */
    public ConcurrentHashMap.KeySetView<String, ConcurrentHashMap<String, AtomicInteger>> getSwitches() {
        return state.keySet();
    }

    public void clearCountOfSentPackets(String switchId, String portNo) {
        ConcurrentHashMap<String, AtomicInteger> ports = state.get(switchId);
        if (ports != null) {
            AtomicInteger packets = ports.get(portNo);
            if (packets != null) {
                packets.set(0);
            }
        }
    }
}
