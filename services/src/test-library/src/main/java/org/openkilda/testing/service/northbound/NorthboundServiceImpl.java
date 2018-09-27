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
import org.openkilda.messaging.command.switches.PortStatus;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.flows.PingInput;
import org.openkilda.northbound.dto.flows.PingOutput;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.switches.PortDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;

import com.google.common.collect.ImmutableMap;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class NorthboundServiceImpl implements NorthboundService {

    private static final String KILDA_CONTROLLER = "kilda";

    @Autowired
    @Qualifier("northboundRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public HealthCheck getHealthCheck() {
        return restTemplate.exchange("/api/v1/health-check", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), HealthCheck.class).getBody();
    }

    @Override
    public FlowPayload getFlow(String flowId) {
        try {
            return restTemplate.exchange("/api/v1/flows/{flow_id}", HttpMethod.GET,
                    new HttpEntity(buildHeadersWithCorrelationId()), FlowPayload.class, flowId).getBody();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }

            return null;
        }
    }

    @Override
    public FlowPayload addFlow(FlowPayload payload) {
        HttpEntity<FlowPayload> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());
        log.debug("Adding flow {}", payload.getId());

        return restTemplate.exchange("/api/v1/flows", HttpMethod.PUT, httpEntity, FlowPayload.class).getBody();
    }

    @Override
    public FlowPayload updateFlow(String flowId, FlowPayload payload) {
        HttpEntity<FlowPayload> httpEntity = new HttpEntity<>(payload, buildHeadersWithCorrelationId());

        return restTemplate.exchange("/api/v1/flows/{flow_id}", HttpMethod.PUT, httpEntity,
                FlowPayload.class, flowId).getBody();
    }

    @Override
    public FlowPayload deleteFlow(String flowId) {
        try {
            return restTemplate.exchange("/api/v1/flows/{flow_id}", HttpMethod.DELETE,
                    new HttpEntity(buildHeadersWithCorrelationId()), FlowPayload.class, flowId).getBody();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }

            return null;
        }
    }

    @Override
    public List<FlowPayload> deleteAllFlows() {
        HttpHeaders httpHeaders = buildHeadersWithCorrelationId();
        httpHeaders.set(Utils.EXTRA_AUTH, String.valueOf(System.currentTimeMillis()));
        FlowPayload[] deletedFlows = restTemplate.exchange("/api/v1/flows", HttpMethod.DELETE,
                new HttpEntity(httpHeaders), FlowPayload[].class).getBody();
        return Arrays.asList(deletedFlows);
    }

    @Override
    public FlowPathPayload getFlowPath(String flowId) {
        return restTemplate.exchange("/api/v1/flows/{flow_id}/path/", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowPathPayload.class, flowId).getBody();
    }

    @Override
    public FlowIdStatusPayload getFlowStatus(String flowId) {
        try {
            return restTemplate.exchange("/api/v1/flows/status/{flow_id}", HttpMethod.GET,
                    new HttpEntity(buildHeadersWithCorrelationId()), FlowIdStatusPayload.class, flowId).getBody();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }

            return null;
        }
    }

    @Override
    public List<FlowPayload> getAllFlows() {
        FlowPayload[] flows = restTemplate.exchange("/api/v1/flows", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowPayload[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public List<Long> deleteSwitchRules(SwitchId switchId) {
        HttpHeaders httpHeaders = buildHeadersWithCorrelationId();
        httpHeaders.set(Utils.EXTRA_AUTH, String.valueOf(System.currentTimeMillis()));

        Long[] deletedRules = restTemplate.exchange(
                "/api/v1/switches/{switch_id}/rules?delete-action=IGNORE_DEFAULTS", HttpMethod.DELETE,
                new HttpEntity(httpHeaders), Long[].class, switchId).getBody();
        return Arrays.asList(deletedRules);
    }

    @Override
    public RulesSyncResult synchronizeSwitchRules(SwitchId switchId) {
        return restTemplate.exchange("/api/v1/switches/{switch_id}/rules/synchronize", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), RulesSyncResult.class, switchId).getBody();
    }

    @Override
    public List<FlowValidationDto> validateFlow(String flowId) {
        FlowValidationDto[] flowValidations = restTemplate.exchange("/api/v1/flows/{flow_id}/validate", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowValidationDto[].class, flowId).getBody();
        return Arrays.asList(flowValidations);
    }

    @Override
    public PingOutput pingFlow(String flowId, PingInput pingInput) {
        HttpEntity<PingInput> httpEntity = new HttpEntity<>(pingInput, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/flows/{flow_id}/ping", HttpMethod.PUT, httpEntity,
                PingOutput.class, flowId).getBody();
    }

    @Override
    public FlowReroutePayload rerouteFlow(String flowId) {
        return restTemplate.exchange("/api/v1/flows/{flowId}/reroute", HttpMethod.PATCH,
                new HttpEntity(buildHeadersWithCorrelationId()), FlowReroutePayload.class, flowId).getBody();
    }

    @Override
    public SwitchFlowEntries getSwitchRules(SwitchId switchId) {
        return restTemplate.exchange("/api/v1/switches/{switch_id}/rules", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchFlowEntries.class, switchId).getBody();
    }

    @Override
    public RulesValidationResult validateSwitchRules(SwitchId switchId) {
        return restTemplate.exchange("/api/v1/switches/{switch_id}/rules/validate", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), RulesValidationResult.class, switchId).getBody();
    }

    @Override
    public List<IslInfoData> getAllLinks() {
        LinkDto[] links = restTemplate.exchange("/api/v1/links", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), LinkDto[].class).getBody();

        return Stream.of(links)
                .map(this::convertToIslInfoData)
                .collect(Collectors.toList());
    }

    @Override
    public List<LinkPropsDto> getAllLinkProps() {
        return getLinkProps(null, null, null, null);
    }

    @Override
    public List<LinkPropsDto> getLinkProps(SwitchId srcSwitch, Integer srcPort, SwitchId dstSwitch, Integer dstPort) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/api/v1/link/props");
        if (srcSwitch != null) {
            uriBuilder.queryParam("src_switch", srcSwitch);
        }
        if (srcPort != null) {
            uriBuilder.queryParam("src_port", srcPort);
        }
        if (dstSwitch != null) {
            uriBuilder.queryParam("dst_switch", dstSwitch);
        }
        if (dstPort != null) {
            uriBuilder.queryParam("dst_port", dstPort);
        }
        LinkPropsDto[] linkProps = restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), LinkPropsDto[].class).getBody();
        return Arrays.asList(linkProps);
    }

    @Override
    public BatchResults updateLinkProps(List<LinkPropsDto> keys) {
        HttpEntity<List<LinkPropsDto>> httpEntity = new HttpEntity<>(keys, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/link/props", HttpMethod.PUT, httpEntity,
                BatchResults.class).getBody();
    }

    @Override
    public BatchResults deleteLinkProps(List<LinkPropsDto> keys) {
        HttpEntity<List<LinkPropsDto>> httpEntity = new HttpEntity<>(keys, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/link/props", HttpMethod.DELETE, httpEntity,
                BatchResults.class).getBody();
    }

    @Override
    public FeatureTogglePayload getFeatureToggles() {
        return restTemplate.exchange("/api/v1/features", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), FeatureTogglePayload.class).getBody();
    }

    @Override
    public FeatureTogglePayload toggleFeature(FeatureTogglePayload request) {
        HttpEntity<FeatureTogglePayload> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/features", HttpMethod.POST, httpEntity,
                FeatureTogglePayload.class).getBody();
    }

    @Override
    public List<SwitchInfoData> getAllSwitches() {
        SwitchDto[] switches = restTemplate.exchange("/api/v1/switches", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchDto[].class).getBody();
        return Stream.of(switches)
                .map(this::convertToSwitchInfoData)
                .collect(Collectors.toList());
    }

    @Override
    public DeleteMeterResult deleteMeter(SwitchId switchId, Integer meterId) {
        return restTemplate.exchange("/api/v1/switches/{switch_id}/meter/{meter_id}", HttpMethod.DELETE,
                new HttpEntity(buildHeadersWithCorrelationId()), DeleteMeterResult.class, switchId, meterId).getBody();
    }

    @Override
    public PortDto configurePort(SwitchId switchId, Integer portNo, Object config) {
        HttpEntity<Object> httpEntity = new HttpEntity<>(config, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v1/switches/{switch_id}/port/{port_id}/config", HttpMethod.PUT,
                httpEntity, PortDto.class, switchId, portNo).getBody();
    }

    @Override
    public PortDto portUp(SwitchId switchId, Integer portNo) {
        log.debug("Bringing port up for switch {}, port {}", switchId, portNo);
        return configurePort(switchId, portNo, ImmutableMap.of("status", PortStatus.UP));
    }

    @Override
    public PortDto portDown(SwitchId switchId, Integer portNo) {
        log.debug("Bringing port down for switch {}, port {}", switchId, portNo);
        return configurePort(switchId, portNo, ImmutableMap.of("status", PortStatus.DOWN));
    }

    @Override
    public List<PortDescription> getPorts(SwitchId switchId) {
        SwitchPortsDescription ports = restTemplate.exchange("/api/v1/switches/{switch_id}/ports", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchPortsDescription.class, switchId).getBody();
        return ports.getPortsDescription();
    }

    @Override
    public PortDescription getPort(SwitchId switchId, Integer portNo) {
        return restTemplate.exchange("/api/v1/switches/{switch_id}/ports/{port_id}", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), PortDescription.class, switchId, portNo).getBody();
    }

    private HttpHeaders buildHeadersWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        return headers;
    }

    private IslInfoData convertToIslInfoData(LinkDto dto) {
        List<PathNode> path = dto.getPath().stream()
                .map(pathDto -> new PathNode(
                        new SwitchId(pathDto.getSwitchId()),
                        pathDto.getPortNo(),
                        pathDto.getSeqId(),
                        pathDto.getSegLatency()))
                .collect(Collectors.toList());
        return new IslInfoData(0, path, dto.getSpeed(), IslChangeType.from(dto.getState().toString()),
                dto.getAvailableBandwidth());
    }

    private SwitchInfoData convertToSwitchInfoData(SwitchDto dto) {
        return new SwitchInfoData(
                new SwitchId(dto.getSwitchId()),
                SwitchState.from(dto.getState()),
                dto.getAddress(),
                dto.getHostname(),
                dto.getDescription(),
                KILDA_CONTROLLER);
    }
}
