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

import org.openkilda.model.SwitchId;
import org.openkilda.testing.service.labservice.LabService;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@Profile("hardware")
public class LockKeeperServiceImpl implements LockKeeperService {

    @Autowired
    LabService labService;

    @Autowired
    @Qualifier("lockKeeperRestTemplate")
    protected RestTemplate restTemplate;

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/flows", HttpMethod.POST,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/flows", HttpMethod.DELETE,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        ASwitchFlow[] flows = restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/flows",
                HttpMethod.GET, new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public void portsUp(List<Integer> ports) {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/ports", HttpMethod.POST,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought up ports: {}", ports);
    }

    @Override
    public void portsDown(List<Integer> ports) {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/ports", HttpMethod.DELETE,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought down ports: {}", ports);
    }

    @Override
    public void knockoutSwitch(SwitchId switchId) {
        throw new UnsupportedOperationException(
                "knockoutSwitch operation for a-switch is not available on hardware env");
    }

    @Override
    public void reviveSwitch(SwitchId switchId) {
        throw new UnsupportedOperationException(
                "reviveSwitch operation for a-switch is not available on hardware env");
    }

    @Override
    public void stopFloodlight() {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/floodlight/stop", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), String.class);
        log.debug("Stopping Floodlight");
    }

    @Override
    public void startFloodlight() {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/floodlight/start", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), String.class);
        log.debug("Starting Floodlight");
    }

    @Override
    public void restartFloodlight() {
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/floodlight/restart", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), String.class);
        log.debug("Restarting Floodlight");
    }

    HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
