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
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class NorthboundServiceV2Impl implements NorthboundServiceV2 {

    @Autowired
    @Qualifier("northboundRestTemplate")
    private RestTemplate restTemplate;

    private DateFormat dateFormat = new SimpleDateFormat(StdDateFormat.DATE_FORMAT_STR_ISO8601);

    @Override
    public FlowResponseV2 addFlow(FlowRequestV2 request) {
        HttpEntity<FlowRequestV2> httpEntity = new HttpEntity<>(request, buildHeadersWithCorrelationId());
        return restTemplate.exchange("/api/v2/flows", HttpMethod.POST, httpEntity, FlowResponseV2.class).getBody();
    }

    @Override
    public FlowRerouteResponseV2 rerouteFlow(String flowId) {
        return restTemplate.exchange("/api/v2/flows/{flow_id}/reroute", HttpMethod.POST,
                new HttpEntity<>(buildHeadersWithCorrelationId()), FlowRerouteResponseV2.class, flowId).getBody();
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

    private HttpHeaders buildHeadersWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()));
        return headers;
    }
}
