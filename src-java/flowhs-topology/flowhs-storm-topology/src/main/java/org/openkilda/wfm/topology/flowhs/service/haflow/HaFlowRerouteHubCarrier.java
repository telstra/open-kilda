/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import org.openkilda.messaging.info.reroute.error.RerouteError;

public interface HaFlowRerouteHubCarrier extends HaFlowGenericCarrier {
    /**
     * Sends reroute result status to reroute topology.
     *
     * @param flowId        flow id.
     * @param rerouteError  first error in reroute process if any.
     * @param correlationId correlation id.
     */
    void sendRerouteResultStatus(String flowId, RerouteError rerouteError, String correlationId);
}
