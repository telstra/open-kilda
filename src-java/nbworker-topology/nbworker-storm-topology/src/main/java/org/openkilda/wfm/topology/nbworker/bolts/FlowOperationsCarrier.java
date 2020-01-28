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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.model.FlowPath;
import org.openkilda.model.IslEndpoint;

import java.util.Collection;
import java.util.Set;

public interface FlowOperationsCarrier {
    /**
     * Sends update for periodic ping.
     * @param flowId flow id
     * @param enabled flag
     */
    void emitPeriodicPingUpdate(String flowId, boolean enabled);

    /**
     * Sends reroute request.
     */
    void sendRerouteRequest(Collection<FlowPath> paths, Set<IslEndpoint> affectedIslEndpoints, String reason);
}
