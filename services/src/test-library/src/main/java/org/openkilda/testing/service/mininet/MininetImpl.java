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

package org.openkilda.testing.service.mininet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
public class MininetImpl implements Mininet {

    @Autowired
    @Qualifier("mininetRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    @Qualifier("mininetFlowToolRestTemplate")
    private RestTemplate flowToolRestTemplate;

    @Override
    public Map createTopology(Object topology) {
        return restTemplate.exchange("/topology", HttpMethod.POST,
                new HttpEntity<>(topology, buildJsonHeaders()), Map.class).getBody();
    }

    @Override
    public void deleteTopology() {
        restTemplate.exchange("/cleanup", HttpMethod.POST,
                new HttpEntity<>("", buildJsonHeaders()), String.class);
    }

    @Override
    public void knockoutSwitch(String switchName) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/knockoutswitch");
        uriBuilder.queryParam("switch", switchName);
        flowToolRestTemplate.exchange(uriBuilder.build().toString(), HttpMethod.POST,
                new HttpEntity<>("", buildJsonHeaders()), String.class);

    }

    @Override
    public void revive(String switchName, String controller) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/reviveswitch");
        uriBuilder.queryParam("switch", switchName);
        uriBuilder.queryParam("controller", controller);
        flowToolRestTemplate.exchange(uriBuilder.build().toString(), HttpMethod.POST,
                new HttpEntity<>("", buildJsonHeaders()), String.class);
    }

    @Override
    public void addFlow(String switchName, Integer inPort, Integer outPort) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/add_transit_flow");
        uriBuilder.queryParam("switch", switchName);
        uriBuilder.queryParam("inport", inPort);
        uriBuilder.queryParam("outport", outPort);
        restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity(buildJsonHeaders()), String.class);

    }

    @Override
    public void removeFlow(String switchName, Integer inPort) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/remove_transit_flow");
        uriBuilder.queryParam("switch", switchName);
        uriBuilder.queryParam("inport", inPort);
        restTemplate.exchange(uriBuilder.build().toString(), HttpMethod.GET,
                new HttpEntity(buildJsonHeaders()), String.class);
    }

    @Override
    public void portUp(String switchName, Integer port) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/port/up");
        uriBuilder.queryParam("switch", switchName);
        uriBuilder.queryParam("port", port);
        flowToolRestTemplate.exchange(uriBuilder.build().toString(), HttpMethod.POST,
                new HttpEntity<>("", buildJsonHeaders()), String.class);
    }

    @Override
    public void portDown(String switchName, Integer port) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/port/down");
        uriBuilder.queryParam("switch", switchName);
        uriBuilder.queryParam("port", port);
        flowToolRestTemplate.exchange(uriBuilder.build().toString(), HttpMethod.POST,
                new HttpEntity<>("", buildJsonHeaders()), String.class);
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
