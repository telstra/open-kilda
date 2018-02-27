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

package org.openkilda.atdd.staging.service.impl;

import org.openkilda.atdd.staging.service.NorthboundService;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NorthboundServiceImpl implements NorthboundService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NorthboundServiceImpl.class);

    @Autowired
    @Qualifier("northboundRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public HealthCheck getHealthCheck() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        return restTemplate.exchange("/api/v1/health-check", HttpMethod.GET,
                new HttpEntity(headers), HealthCheck.class).getBody();
    }

    @Override
    public FlowPayload getFlow(String flowId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));

        Map<String, Object> params = new HashMap<>();
        params.put("flow_id", flowId);

        return restTemplate.exchange("/api/v1/flows/{flow_id}", HttpMethod.GET,
                new HttpEntity(headers), FlowPayload.class, params).getBody();
    }

    @Override
    public FlowPayload addFlow(FlowPayload payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        HttpEntity<FlowPayload> httpEntity = new HttpEntity<>(payload, headers);

        return restTemplate.exchange("/api/v1/flows", HttpMethod.PUT,
                httpEntity, FlowPayload.class).getBody();
    }

    @Override
    public FlowPayload updateFlow(String flowId, FlowPayload payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        HttpEntity<FlowPayload> httpEntity = new HttpEntity<>(payload, headers);

        Map<String, Object> params = new HashMap<>();
        params.put("flow_id", flowId);

        return restTemplate.exchange("/api/v1/flows/{flow_id}", HttpMethod.PUT,
                httpEntity, FlowPayload.class, params).getBody();
    }

    @Override
    public FlowPayload deleteFlow(String flowId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));

        Map<String, Object> params = new HashMap<>();
        params.put("flow_id", flowId);

        return restTemplate.exchange("/api/v1/flows/{flow_id}", HttpMethod.DELETE,
                new HttpEntity(headers), FlowPayload.class, params).getBody();
    }

    @Override
    public FlowPathPayload getFlowPath(String flowId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));

        Map<String, Object> params = new HashMap<>();
        params.put("flow_id", flowId);

        return restTemplate.exchange("/api/v1/flows/path/{flow_id}", HttpMethod.GET,
                new HttpEntity(headers), FlowPathPayload.class, params).getBody();
    }

    @Override
    public String getFlowStatus(String flowId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));

        Map<String, Object> params = new HashMap<>();
        params.put("flow_id", flowId);

        return restTemplate.exchange("/api/v1/flows/status/{flow_id}", HttpMethod.GET,
                new HttpEntity(headers), String.class, params).getBody();
    }

    @Override
    public List<FlowPayload> getFlowDump() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));

        FlowPayload[] flows = restTemplate.exchange("/api/v1/flows", HttpMethod.GET,
                new HttpEntity(headers), FlowPayload[].class).getBody();
        return Arrays.asList(flows);
    }
}
