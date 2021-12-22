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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.topology.flowhs.service.common.LifecycleEventCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;

import java.util.List;

public interface FlowValidationHubCarrier extends NorthboundResponseCarrier, LifecycleEventCarrier {
    /**
     * Sends a command to speaker.
     */
    void sendSpeakerRequest(String flowId, CommandData commandData);

    /**
     * Sends a response to Northbound.
     */
    void sendNorthboundResponse(List<? extends InfoData> messageData);
}
