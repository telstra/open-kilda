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
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
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

import java.util.Arrays;
import java.util.List;

@Service
public class NorthboundServiceImpl implements NorthboundService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NorthboundServiceImpl.class);

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
    public RulesValidationResult validateSwitchRules(String switchId) {
            return restTemplate.exchange("/api/v1/switches/{switch_id}/rules/validate", HttpMethod.GET,
                new HttpEntity(buildHeadersWithCorrelationId()), RulesValidationResult.class, switchId).getBody();
    }

    private HttpHeaders buildHeadersWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        return headers;
    }
}
