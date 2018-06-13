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

package org.openkilda.atdd.staging.service.northbound;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class NorthboundServiceImpl implements NorthboundService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NorthboundServiceImpl.class);

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
        return restTemplate.exchange("/api/v1/flows/path/{flow_id}", HttpMethod.GET,
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
    public List<Long> deleteSwitchRules(String switchId) {
        HttpHeaders httpHeaders = buildHeadersWithCorrelationId();
        httpHeaders.set(Utils.EXTRA_AUTH, String.valueOf(System.currentTimeMillis()));

        Long[] deletedRules = restTemplate.exchange(
                "/api/v1/switches/{switch_id}/rules?delete-action=IGNORE_DEFAULTS", HttpMethod.DELETE,
                new HttpEntity(httpHeaders), Long[].class, switchId).getBody();
        return Arrays.asList(deletedRules);
    }

    @Override
    public RulesSyncResult synchronizeSwitchRules(String switchId) {
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
    public SwitchFlowEntries getSwitchRules(String switchId) {
        return restTemplate.exchange("/api/v1/switches/{switch_id}/rules", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), SwitchFlowEntries.class, switchId).getBody();
    }

    @Override
    public RulesValidationResult validateSwitchRules(String switchId) {
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
    public List<LinkPropsDto> getLinkProps(String srcSwitch, Integer srcPort, String dstSwitch, Integer dstPort) {
        String uri = UriComponentsBuilder.fromUriString("/api/v1/link/props")
                .queryParam("src_switch", srcSwitch)
                .queryParam("dst_switch", dstSwitch)
                .queryParam("src_port", srcPort)
                .queryParam("dst_port", dstPort)
                .build().toString();
        LinkPropsDto[] linkProps = restTemplate.exchange(uri, HttpMethod.GET,
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

    private HttpHeaders buildHeadersWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        return headers;
    }

    private IslInfoData convertToIslInfoData(LinkDto dto) {
        List<PathNode> path = dto.getPath().stream()
                .map(pathDto -> new PathNode(pathDto.getSwitchId(), pathDto.getPortNo(), pathDto.getSeqId(),
                        pathDto.getSegLatency()))
                .collect(Collectors.toList());
        return new IslInfoData(0, path, dto.getSpeed(), IslChangeType.from(dto.getState().toString()),
                dto.getAvailableBandwidth());
    }

    private SwitchInfoData convertToSwitchInfoData(SwitchDto dto) {
        return new SwitchInfoData(dto.getSwitchId(), SwitchState.from(dto.getState()), dto.getAddress(),
                dto.getHostname(), dto.getDescription(), KILDA_CONTROLLER);
    }
}
