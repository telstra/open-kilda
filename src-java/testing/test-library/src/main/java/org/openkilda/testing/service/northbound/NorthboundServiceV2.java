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
import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatusesResponse;
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload;
import org.openkilda.northbound.dto.v2.flows.FlowLoopResponse;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointsResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.links.BfdProperties;
import org.openkilda.northbound.dto.v2.links.BfdPropertiesPayload;
import org.openkilda.northbound.dto.v2.switches.LagPortRequest;
import org.openkilda.northbound.dto.v2.switches.LagPortResponse;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDevicesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.dto.v2.switches.SwitchPropertiesDump;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowSyncResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult;
import org.openkilda.testing.model.topology.TopologyDefinition;

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

    FlowResponseV2 partialUpdate(String flowId, FlowPatchV2 patch);

    List<FlowLoopResponse> getFlowLoop(String flowId);

    List<FlowLoopResponse> getFlowLoop(SwitchId switchId);

    List<FlowLoopResponse> getFlowLoop(String flowId, SwitchId switchId);

    FlowLoopResponse createFlowLoop(String flowId, FlowLoopPayload flowLoopPayload);

    FlowLoopResponse deleteFlowLoop(String flowId);

    FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId);

    FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId, Long timeFrom, Long timeTo);

    FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId, Integer maxCount);

    FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId, Long timeFrom, Long timeTo, Integer maxCount);

    FlowMirrorPointResponseV2 createMirrorPoint(String flowId, FlowMirrorPointPayload mirrorPoint);

    FlowMirrorPointsResponseV2 getMirrorPoints(String flowId);

    FlowMirrorPointResponseV2 deleteMirrorPoint(String flowId, String mirrorPointId);

    //switches

    SwitchConnectedDevicesResponse getConnectedDevices(SwitchId switchId);

    SwitchConnectedDevicesResponse getConnectedDevices(SwitchId switchId, Date since);

    List<PortHistoryResponse> getPortHistory(SwitchId switchId, Integer port);

    List<PortHistoryResponse> getPortHistory(SwitchId switchId, Integer port, Long timeFrom, Long timeTo);

    PortPropertiesResponse getPortProperties(SwitchId switchId, Integer port);

    PortPropertiesResponse updatePortProperties(SwitchId switchId, Integer port, PortPropertiesDto payload);

    SwitchDtoV2 partialSwitchUpdate(SwitchId switchId, SwitchPatchDto dto);

    SwitchConnectionsResponse getSwitchConnections(SwitchId switchId);

    SwitchPropertiesDump getAllSwitchProperties();

    List<LagPortResponse> getLagLogicalPort(SwitchId switchId);

    LagPortResponse createLagLogicalPort(SwitchId switchId, LagPortRequest payload);

    LagPortResponse updateLagLogicalPort(SwitchId switchId, Integer logicalPortNumber, LagPortRequest payload);

    LagPortResponse deleteLagLogicalPort(SwitchId switchId, Integer logicalPortNumber);

    //links
    BfdPropertiesPayload setLinkBfd(TopologyDefinition.Isl isl);

    BfdPropertiesPayload setLinkBfd(TopologyDefinition.Isl isl, BfdProperties props);

    BfdPropertiesPayload setLinkBfd(SwitchId srcSwId, Integer srcPort, SwitchId dstSwId, Integer dstPort,
                                    BfdProperties props);

    void deleteLinkBfd(SwitchId srcSwId, Integer srcPort, SwitchId dstSwId, Integer dstPort);

    void deleteLinkBfd(TopologyDefinition.Isl isl);

    BfdPropertiesPayload getLinkBfd(SwitchId srcSwId, Integer srcPort, SwitchId dstSwId, Integer dstPort);

    BfdPropertiesPayload getLinkBfd(TopologyDefinition.Isl isl);

    //y-flows
    YFlow getYFlow(String yFlowId);

    List<YFlow> getAllYFlows();

    YFlow addYFlow(YFlowCreatePayload request);

    YFlow updateYFlow(String yFlowId, YFlowUpdatePayload request);

    YFlow partialUpdateYFlow(String yFlowId, YFlowPatchPayload request);

    YFlow deleteYFlow(String yFlowId);

    YFlowRerouteResult rerouteYFlow(String yFlowId);

    YFlowPaths getYFlowPaths(String yFlowId);

    YFlowValidationResult validateYFlow(String yFlowId);

    YFlowSyncResult synchronizeYFlow(String yFlowId);

    YFlowPingResult pingYFlow(String yFlowId, YFlowPingPayload payload);

    YFlow swapYFlowPaths(String yFlowId);
}
