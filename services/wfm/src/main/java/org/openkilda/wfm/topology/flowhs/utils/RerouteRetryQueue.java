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

import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import java.util.Collections;
import java.util.Optional;

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
        return Optional.ofNullable(active);
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
        // Clean list of affected paths to force reroute of all paths related to the flow.
        // Reroute topology fill's affected paths set from affected network segment. It know only already stored
        // into DB paths, so it will produce reroute requests for currently stored paths. For thew first glance we can
        // ignore requests that mention not exists any more paths. But because old and new paths can share network
        // segments (this segments have been healthy during paths allocation) we can't ignore such request and must
        // perform reroute for them.
        //
        // A --- B --- C  original-path (path1)
        // A -x- B --- C  first network outage - emit reroute for path1
        // A --- D --- C  path after reroute (path2 - reroute in progress)
        // A --- D -x- C  second network outage - emit reroute for path1
        // done reroute for path1, fetch postponed reroute request (it targeted to path1)
        //
        // So if this postponed request will be ignored, because path1 is not related to the flow any more
        // we will leave flow in UP state on ISL in FAILED state.
        return new FlowRerouteFact(
                reroute.getKey(), reroute.getCommandContext(), reroute.getFlowId(), Collections.emptySet(),
                isForced, reroute.getRerouteReason());
    }
}
