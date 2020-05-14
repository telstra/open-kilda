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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.messaging.info.reroute.error.RerouteError;

public interface FlowRerouteHubCarrier extends FlowGenericCarrier {
    /**
     * Cancels timeout callback.
     *
     * @param key operation identifier.
     */
    void cancelTimeoutCallback(String key);

    /**
     * Sends reroute result status to reroute topology.
     *
     * @param flowId flow id.
     * @param success whether reroute succeed or not.
     * @param rerouteError first error in reroute process if any.
     * @param correlationId correlation id.
     */
    void sendRerouteResultStatus(String flowId, boolean success, RerouteError rerouteError, String correlationId);
}
