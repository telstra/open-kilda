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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.model.PathId;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class RerouteRetryQueue {
    private FlowRerouteFact active = null;
    private FlowRerouteFact pending = null;

    /**
     * Add request into "queue".
     */
    public void add(FlowRerouteFact reroute) {
        if (active == null) {
            active = reroute;
        } else if (pending == null) {
            pending = reroute;
        } else {
            pending = mergePending(pending, reroute);
        }
    }

    /**
     * Return first/active queue entry.
     */
    public Optional<FlowRerouteFact> get() {
        FlowRerouteFact result = active;
        if (result == null) {
            result = pending;
        }
        return Optional.ofNullable(result);
    }

    /**
     * Remove and return first/active queue entry.
     */
    public Optional<FlowRerouteFact> remove() {
        FlowRerouteFact result = active;
        active = pending;
        pending = null;
        return Optional.ofNullable(result);
    }

    /**
     * Return queue size.
     */
    public int size() {
        if (pending != null) {
            return 2;
        }
        if (active != null) {
            return 1;
        }
        return 0;
    }

    public boolean isEmpty() {
        return active == null;
    }

    private static FlowRerouteFact mergePending(FlowRerouteFact pending, FlowRerouteFact reroute) {
        boolean isForced = pending.isForceReroute() || reroute.isForceReroute();
        Set<PathId> paths = new HashSet<>();
        if (pending.getPathsToReroute() != null) {
            paths.addAll(pending.getPathsToReroute());
        }
        if (reroute.getPathsToReroute() != null) {
            paths.addAll(reroute.getPathsToReroute());
        }

        return new FlowRerouteFact(
                reroute.getKey(), reroute.getCommandContext(), reroute.getFlowId(), paths, isForced,
                reroute.getRerouteReason());
    }
}
