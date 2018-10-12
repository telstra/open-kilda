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

package org.openkilda.pce.impl.model;

import org.openkilda.model.SwitchId;

import lombok.NonNull;
import lombok.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is just a summary of what a switch is; sufficient for path computation.
 */
@Value
public class SimpleSwitch {

    /**
     * The DPID is used as the primary key in most/all places.
     */
    @NonNull
    public final SwitchId dpid;

    /**
     * We are mostly interested in who this switch connects with. It might have multiple connections
     * to the same switch over different ports. Allow for this by creating an map of lists.
     * <p/>
     * key (String) = The destination switch dpid.
     */
    public final Map<SwitchId, Set<SimpleIsl>> outbound = new HashMap<>();

    public void addOutbound(SimpleIsl isl) {
        outbound.computeIfAbsent(isl.getDstDpid(), newSet -> new HashSet<>()).add(isl);
    }
}
