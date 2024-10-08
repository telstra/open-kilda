/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.service;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.flow.FlowResponse;

public interface IRerouteQueueCarrier {

    void sendRerouteRequest(String correlationId, FlowRerouteRequest request);

    void sendRerouteRequest(String correlationId, YFlowRerouteRequest request);

    void sendRerouteRequest(String correlationId, HaFlowRerouteRequest request);

    void emitFlowRerouteError(ErrorData errorData);

    void emitFlowRerouteInfo(FlowResponse flowData);

    void sendExtendTimeWindowEvent();

    void cancelTimeout(String key);
}
