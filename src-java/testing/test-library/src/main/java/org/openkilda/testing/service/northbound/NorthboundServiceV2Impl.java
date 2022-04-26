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

package org.openkilda.testing.service.northbound;

import org.openkilda.messaging.Utils;
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
import org.openkilda.northbound.dto.v2.yflows.SubFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowDump;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowSyncResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult;
import org.openkilda.testing.model.topology.TopologyDefinition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Service
@Slf4j
public class NorthboundServiceV2Impl implements NorthboundServiceV2 {

    @Autowired
    @Qualifier("northboundRestTemplate")
    private RestTemplate restTemplate;

    // ISO format, with a colon in the timezone field.
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Override
    public FlowResponseV2 getFlow(String flowId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowResponseV2.class, flowId).getBody();
    }

    @Override
    public List<FlowResponseV2> getAllFlows() {
        FlowResponseV2[] flows = restTemplate.exchange("/api/v2/flows", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowResponseV2[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public FlowIdStatusPayload getFlowStatus(String flowId) {
        try {
            return restTemplate.exchange("/api/v2/flows/status/{flow_id}", HttpMethod.GET, new HttpEntity(
                    buildHeadersWithCorrelationId()), FlowIdStatusPayload.class, flowId).getBody();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
            return null;
        }
    }

    @Override
    public FlowResponseV2 addFlow(FlowRequestV2 request) {
        HttpEntity<FlowRequestV2> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/flows", HttpMethod.POST, httpEntity, FlowResponseV2.class).getBody();
    }

    @Override
    public FlowResponseV2 updateFlow(String flowId, FlowRequestV2 request) {
        HttpEntity<FlowRequestV2> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/flows/{flow_id}", HttpMethod.PUT, httpEntity, FlowResponseV2.class,
                flowId).getBody();
    }

    @Override
    public FlowResponseV2 deleteFlow(String flowId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}", HttpMethod.DELETE,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowResponseV2.class, flowId).getBody();
    }

    @Override
    public FlowRerouteResponseV2 rerouteFlow(String flowId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}/reroute", HttpMethod.POST,
                new HttpEntity<>(buildHeadersWithCorrelationId()), FlowRerouteResponseV2.class, flowId).getBody();
    }

    @Override
    public FlowResponseV2 partialUpdate(String flowId, FlowPatchV2 patch) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}", HttpMethod.PATCH,
                new HttpEntity<>(patch, buildHeadersWithCorrelationId()), FlowResponseV2.class, flowId)
                .getBody();
    }

    @Override
    public List<FlowLoopResponse> getFlowLoop(String flowId) {
        return getFlowLoop(flowId, null);
    }

    @Override
    public List<FlowLoopResponse> getFlowLoop(SwitchId switchId) {
        return getFlowLoop(null, switchId);
    }

    @Override
    public List<FlowLoopResponse> getFlowLoop(String flowId, SwitchId switchId) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/api/v2/flows/loops");
        if (flowId != null) {
            uriBuilder.queryParam("flow_id", flowId);
        }
        if (switchId != null) {
            uriBuilder.queryParam("switch_id", switchId);
        }
        FlowLoopResponse[] flowLoopResponse = restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowLoopResponse[].class).getBody();
        return Arrays.asList(flowLoopResponse);
    }

    @Override
    public FlowLoopResponse createFlowLoop(String flowId, FlowLoopPayload flowLoopPayload) {
        HttpEntity<FlowLoopPayload> httpEntity = new HttpEntity<>(flowLoopPayload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/flows/{flow_id}/loops", HttpMethod.POST, httpEntity,
                FlowLoopResponse.class, flowId).getBody();
    }

    @Override
    public FlowLoopResponse deleteFlowLoop(String flowId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}/loops", HttpMethod.DELETE,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowLoopResponse.class, flowId).getBody();
    }

    @Override
    public FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId) {
        return getFlowHistoryStatuses(flowId, null, null, null);
    }

    @Override
    public FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId, Long timeFrom, Long timeTo) {
        return getFlowHistoryStatuses(flowId, timeFrom, timeTo, null);
    }

    @Override
    public FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId, Integer maxCount) {
        return getFlowHistoryStatuses(flowId, null, null, maxCount);
    }

    @Override
    public FlowHistoryStatusesResponse getFlowHistoryStatuses(String flowId, Long timeFrom, Long timeTo,
                                                              Integer maxCount) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString("/api/v2/flows/{flow_id}/history/statuses");
        if (timeFrom != null) {
            uriBuilder.queryParam("timeFrom", timeFrom);
        }
        if (timeTo != null) {
            uriBuilder.queryParam("timeTo", timeTo);
        }
        if (maxCount != null) {
            uriBuilder.queryParam("max_count", maxCount);
        }

        return restTemplate.exchange(uriBuilder.build().toString(),
                HttpMethod.GET, new HttpEntity(buildHeadersWithCorrelationId()), FlowHistoryStatusesResponse.class,
                flowId).getBody();
    }

    @Override
    public FlowMirrorPointResponseV2 createMirrorPoint(String flowId, FlowMirrorPointPayload mirrorPoint) {
        HttpEntity<FlowMirrorPointPayload> httpEntity = new HttpEntity<>(mirrorPoint, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/flows/{flow_id}/mirror", HttpMethod.POST, httpEntity,
                FlowMirrorPointResponseV2.class, flowId).getBody();
    }

    @Override
    public FlowMirrorPointsResponseV2 getMirrorPoints(String flowId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}/mirror", HttpMethod.GET,
                new HttpEntity<>(buildHeadersWithCorrelationId()), FlowMirrorPointsResponseV2.class, flowId).getBody();
    }

    @Override
    public FlowMirrorPointResponseV2 deleteMirrorPoint(String flowId, String mirrorPointId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}/mirror/{mirror_point_id}", HttpMethod.DELETE,
                new HttpEntity<>(buildHeadersWithCorrelationId()), FlowMirrorPointResponseV2.class, flowId,
                mirrorPointId).getBody();
    }

    @Override
    public SwitchConnectedDevicesResponse getConnectedDevices(SwitchId switchId) {
        return getConnectedDevices(switchId, null);
    }

    @Override
    public SwitchConnectedDevicesResponse getConnectedDevices(SwitchId switchId, Date since) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/api/v2/switches/{switch_id}/devices");
        if (since != null) {
            uriBuilder.queryParam("since", dateFormat.format(since));
        }
        return restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchConnectedDevicesResponse.class,
                switchId).getBody();
    }

    @Override
    public List<PortHistoryResponse> getPortHistory(SwitchId switchId, Integer port) {
        return getPortHistory(switchId, port, null, null);
    }

    @Override
    public List<PortHistoryResponse> getPortHistory(SwitchId switchId, Integer port, Long timeFrom, Long timeTo) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(
                "/api/v2/switches/{switch_id}/ports/{port}/history");
        if (timeFrom != null) {
            uriBuilder.queryParam("timeFrom", dateFormat.format(new Date(timeFrom)));
        }
        if (timeTo != null) {
            uriBuilder.queryParam("timeTo", dateFormat.format(new Date(timeTo)));
        }

        PortHistoryResponse[] portHistory = restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), PortHistoryResponse[].class, switchId, port).getBody();
        return Arrays.asList(portHistory);
    }

    @Override
    public PortPropertiesResponse getPortProperties(SwitchId switchId, Integer port) {
        return restTemplate.exchange("/api/v2/switches/{switch_id}/ports/{port}/properties", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), PortPropertiesResponse.class,
                switchId, port).getBody();
    }

    @Override
    public PortPropertiesResponse updatePortProperties(SwitchId switchId, Integer port, PortPropertiesDto payload) {
        log.debug("Update port property. Switch: {}, port: {}, enableDiscovery: {}.", switchId, port,
                payload.isDiscoveryEnabled());
        return restTemplate.exchange("/api/v2/switches/{switch_id}/ports/{port}/properties", HttpMethod.PUT,
                new HttpEntity<>(payload, buildHeadersWithCorrelationId()), PortPropertiesResponse.class,
                switchId, port).getBody();
    }

    @Override
    public SwitchDtoV2 partialSwitchUpdate(SwitchId switchId, SwitchPatchDto dto) {
        return restTemplate.exchange("/api/v2/switches/{switchId}", HttpMethod.PATCH,
                new HttpEntity<>(dto, buildHeadersWithCorrelationId()), SwitchDtoV2.class, switchId)
                .getBody();
    }

    @Override
    public SwitchConnectionsResponse getSwitchConnections(SwitchId switchId) {
        log.debug("Get switch('{}') connections", switchId);
        return restTemplate.exchange("/api/v2/switches/{switchId}/connections", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchConnectionsResponse.class, switchId).getBody();
    }

    @Override
    public SwitchPropertiesDump getAllSwitchProperties() {
        log.debug("Get all switch properties");
        return restTemplate.exchange("/api/v2/switches/properties", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchPropertiesDump.class).getBody();
    }

    @Override
    public List<LagPortResponse> getLagLogicalPort(SwitchId switchId) {
        log.debug("Get LAG ports from switch('{}')", switchId);
        LagPortResponse[] lagPorts = restTemplate.exchange("/api/v2/switches/{switch_id}/lags", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), LagPortResponse[].class, switchId).getBody();
        return Arrays.asList(lagPorts);
    }

    @Override
    public LagPortResponse createLagLogicalPort(SwitchId switchId, LagPortRequest payload) {
        log.debug("Create LAG port on switch('{}')", switchId);
        HttpEntity<LagPortRequest> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/switches/{switch_id}/lags", HttpMethod.POST, httpEntity,
                LagPortResponse.class, switchId).getBody();
    }

    @Override
    public LagPortResponse updateLagLogicalPort(SwitchId switchId, Integer logicalPortNumber, LagPortRequest payload) {
        log.debug("Create LAG port on switch('{}')", switchId);
        HttpEntity<LagPortRequest> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/switches/{switch_id}/lags/{logical_port_number}", HttpMethod.PUT,
                httpEntity, LagPortResponse.class, switchId, logicalPortNumber).getBody();
    }

    @Override
    public LagPortResponse deleteLagLogicalPort(SwitchId switchId, Integer logicalPortNumber) {
        log.debug("Delete LAG port('{}') from switch('{}')", logicalPortNumber, switchId);
        return restTemplate.exchange("/api/v2/switches/{switch_id}/lags/{logical_port_number}", HttpMethod.DELETE,
                new HttpEntity<>(buildHeadersWithCorrelationId()), LagPortResponse.class, switchId,
                logicalPortNumber).getBody();
    }

    @Override
    public BfdPropertiesPayload setLinkBfd(TopologyDefinition.Isl isl) {
        return setLinkBfd(isl, new BfdProperties(350L, (short) 3));
    }

    @Override
    public BfdPropertiesPayload setLinkBfd(TopologyDefinition.Isl isl, BfdProperties props) {
        return setLinkBfd(isl.getSrcSwitch().getDpId(), isl.getSrcPort(), isl.getDstSwitch().getDpId(),
                isl.getDstPort(), props);
    }

    @Override
    public BfdPropertiesPayload setLinkBfd(SwitchId srcSwId, Integer srcPort, SwitchId dstSwId, Integer dstPort,
                                           BfdProperties props) {
        return restTemplate.exchange("/api/v2/links/{src-switch}_{src-port}/{dst-switch}_{dst-port}/bfd",
                HttpMethod.PUT, new HttpEntity<>(props, buildHeadersWithCorrelationId()), BfdPropertiesPayload.class,
                srcSwId, srcPort, dstSwId, dstPort).getBody();
    }

    @Override
    public void deleteLinkBfd(SwitchId srcSwId, Integer srcPort, SwitchId dstSwId, Integer dstPort) {
        restTemplate.exchange("/api/v2/links/{src-switch}_{src-port}/{dst-switch}_{dst-port}/bfd",
                HttpMethod.DELETE, new HttpEntity<>(buildHeadersWithCorrelationId()), Void.class, srcSwId, srcPort,
                dstSwId, dstPort);
    }

    @Override
    public void deleteLinkBfd(TopologyDefinition.Isl isl) {
        deleteLinkBfd(isl.getSrcSwitch().getDpId(), isl.getSrcPort(), isl.getDstSwitch().getDpId(), isl.getDstPort());
    }

    @Override
    public BfdPropertiesPayload getLinkBfd(SwitchId srcSwId, Integer srcPort, SwitchId dstSwId, Integer dstPort) {
        return restTemplate.exchange("/api/v2/links/{src-switch}_{src-port}/{dst-switch}_{dst-port}/bfd",
                HttpMethod.GET, new HttpEntity<>(buildHeadersWithCorrelationId()), BfdPropertiesPayload.class,
                srcSwId, srcPort, dstSwId, dstPort).getBody();
    }

    @Override
    public BfdPropertiesPayload getLinkBfd(TopologyDefinition.Isl isl) {
        return getLinkBfd(isl.getSrcSwitch().getDpId(), isl.getSrcPort(), isl.getDstSwitch().getDpId(),
                isl.getDstPort());
    }

    @Override
    public YFlow getYFlow(String yFlowId) {
        try {
            return sorted(restTemplate.exchange("/api/v2/y-flows/{y_flow_id}", HttpMethod.GET,
                    new HttpEntity(buildHeadersWithCorrelationId()), YFlow.class, yFlowId).getBody());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
            return null;
        }
    }

    @Override
    public List<YFlow> getAllYFlows() {
        return sorted(restTemplate.exchange("/api/v2/y-flows", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), YFlowDump.class).getBody().getYFlows());
    }

    @Override
    public YFlow addYFlow(YFlowCreatePayload request) {
        HttpEntity<YFlowCreatePayload> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return sorted(restTemplate.exchange("/api/v2/y-flows", HttpMethod.POST, httpEntity, YFlow.class).getBody());
    }

    @Override
    public YFlow updateYFlow(String yFlowId, YFlowUpdatePayload request) {
        HttpEntity<YFlowUpdatePayload> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return sorted(restTemplate.exchange("/api/v2/y-flows/{y_flow_id}", HttpMethod.PUT, httpEntity, YFlow.class,
                yFlowId).getBody());
    }

    @Override
    public YFlow partialUpdateYFlow(String yFlowId, YFlowPatchPayload request) {
        HttpEntity<YFlowPatchPayload> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return sorted(restTemplate.exchange("/api/v2/y-flows/{y_flow_id}", HttpMethod.PATCH, httpEntity, YFlow.class,
                yFlowId).getBody());
    }

    @Override
    public YFlow deleteYFlow(String yFlowId) {
        return sorted(restTemplate.exchange("/api/v2/y-flows/{y_flow_id}", HttpMethod.DELETE,
                new HttpEntity(buildHeadersWithCorrelationId()), YFlow.class, yFlowId).getBody());
    }

    @Override
    public YFlowRerouteResult rerouteYFlow(String yFlowId) {
        return restTemplate.exchange("/api/v2/y-flows/{y_flow_id}/reroute", HttpMethod.POST,
                new HttpEntity<>(buildHeadersWithCorrelationId()), YFlowRerouteResult.class, yFlowId).getBody();
    }

    @Override
    public YFlowPaths getYFlowPaths(String yFlowId) {
        return restTemplate.exchange("/api/v2/y-flows/{y_flow_id}/paths", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), YFlowPaths.class, yFlowId).getBody();
    }

    @Override
    public YFlowValidationResult validateYFlow(String yFlowId) {
        return restTemplate.exchange("/api/v2/y-flows/{y_flow_id}/validate", HttpMethod.POST,
                new HttpEntity<>(buildHeadersWithCorrelationId()), YFlowValidationResult.class, yFlowId).getBody();
    }

    @Override
    public YFlowSyncResult synchronizeYFlow(String yFlowId) {
        return restTemplate.exchange("/api/v2/y-flows/{y_flow_id}/sync", HttpMethod.POST,
                new HttpEntity<>(buildHeadersWithCorrelationId()), YFlowSyncResult.class, yFlowId).getBody();
    }

    @Override
    public YFlowPingResult pingYFlow(String yFlowId, YFlowPingPayload payload) {
        HttpEntity<YFlowPingPayload> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/y-flows/{y_flow_id}/ping", HttpMethod.POST, httpEntity,
                YFlowPingResult.class, yFlowId).getBody();
    }

    @Override
    public YFlow swapYFlowPaths(String yFlowId) {
        return restTemplate.exchange("/api/v2/y-flows/{y_flow_id}/swap", HttpMethod.POST,
                new HttpEntity<>(buildHeadersWithCorrelationId()), YFlow.class, yFlowId).getBody();
    }

    private HttpHeaders buildHeadersWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, "fn-tests-" + UUID.randomUUID().toString());
        return headers;
    }

    private YFlow sorted(@Nullable YFlow yFlow) {
        yFlow.getSubFlows().sort(Comparator.comparing(SubFlow::getFlowId));
        return yFlow;
    }

    private List<YFlow> sorted(List<YFlow> yFlows) {
        return yFlows.stream().map(this::sorted).collect(Collectors.toList());
    }
}
