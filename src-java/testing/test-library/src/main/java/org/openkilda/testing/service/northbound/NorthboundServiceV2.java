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

package org.openkilda.testing.service.northbound;

import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDevicesResponse;

import java.util.Date;
import java.util.List;

public interface NorthboundServiceV2 {

    //flows

    FlowResponseV2 getFlow(String flowId);

    List<FlowResponseV2> getAllFlows();

    FlowIdStatusPayload getFlowStatus(String flowId);

    FlowResponseV2 addFlow(FlowRequestV2 request);

    FlowResponseV2 updateFlow(String flowId, FlowRequestV2 request);

    FlowResponseV2 deleteFlow(String flowId);

    FlowRerouteResponseV2 rerouteFlow(String flowId);

    //switches

    SwitchConnectedDevicesResponse getConnectedDevices(SwitchId switchId);

    SwitchConnectedDevicesResponse getConnectedDevices(SwitchId switchId, Date since);

    List<PortHistoryResponse> getPortHistory(SwitchId switchId, Integer port);

    List<PortHistoryResponse> getPortHistory(SwitchId switchId, Integer port, Long timeFrom, Long timeTo);

    PortPropertiesResponse getPortProperties(SwitchId switchId, Integer port);

    PortPropertiesResponse updatePortProperties(SwitchId switchId, Integer port, PortPropertiesDto payload);
}
