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

package org.openkilda.testing.service.lockkeeper;

import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is a helper 'testing' service and should not be considered as part of Kilda.
 * Takes control over the intermediate 'A Switch' in Staging which is meant to allow to
 * disconnect ISLs.
 * Also allows to control floodlight's lifecycle.
 */
@Service
@Profile("hardware")
public class LockKeeperServiceImpl implements LockKeeperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockKeeperServiceImpl.class);

    @Autowired
    @Qualifier("lockKeeperRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange("/flows", HttpMethod.POST,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        LOGGER.debug("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange("/flows", HttpMethod.DELETE,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        LOGGER.debug("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        ASwitchFlow[] flows = restTemplate.exchange("/flows", HttpMethod.GET,
                new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public void portsUp(List<Integer> ports) {
        restTemplate.exchange("/ports", HttpMethod.POST,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        LOGGER.debug("Brought up ports: {}", ports);
    }

    @Override
    public void portsDown(List<Integer> ports) {
        restTemplate.exchange("/ports", HttpMethod.DELETE,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        LOGGER.debug("Brought down ports: {}", ports);
    }

    @Override
    public void knockoutSwitch(String switchId) {
        throw new UnsupportedOperationException(
                "knockoutSwitch operation for a-switch is not available on hardware env");
    }

    @Override
    public void reviveSwitch(String switchId, String controllerAddress) {
        throw new UnsupportedOperationException(
                "reviveSwitch operation for a-switch is not available on hardware env");
    }

    @Override
    public void stopController() {
        restTemplate.exchange("/floodlight/stop", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), String.class);
    }

    @Override
    public void startController() {
        restTemplate.exchange("/floodlight/start", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), String.class);
    }

    @Override
    public void restartController() {
        restTemplate.exchange("/floodlight/restart", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), String.class);
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
