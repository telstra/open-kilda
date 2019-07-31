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

import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.labservice.LabService;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.InetAddress;
import org.openkilda.testing.service.northbound.NorthboundService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    NorthboundService northbound;

    @Value("#{'${kafka.bootstrap.server}'.split(':')}")
    private List<String> kafkaBootstrapServer;

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

    @Override
    public void knockoutSwitch(Switch sw) {
        log.debug("Block Floodlight access to switch '{}' by adding iptables rules", sw.getName());
        String swIp = northbound.getSwitch(sw.getDpId()).getAddress();
        restTemplate.exchange(labService.getLab().getLabId() + "/block-floodlight-access", HttpMethod.POST,
                new HttpEntity<>(new InetAddress(swIp), buildJsonHeaders()), String.class);
    }

    @Override
    public void reviveSwitch(Switch sw) {
        log.debug("Unblock Floodlight access to switch '{}' by removing iptables rules", sw.getName());
        String swIp = northbound.getSwitch(sw.getDpId()).getAddress();
        restTemplate.exchange(labService.getLab().getLabId() + "/unblock-floodlight-access", HttpMethod.POST,
                new HttpEntity<>(new InetAddress(swIp), buildJsonHeaders()), String.class);
    }

    @Override
    public void setController(Switch sw, String controller) {
        throw new UnsupportedOperationException(
                "setController method is not available on hardware env");
    }

    @Override
    public void blockFloodlightAccessToPort(Integer port) {
        log.debug("Block floodlight access to {} by adding iptables rules", port);
        restTemplate.exchange(labService.getLab().getLabId() + "/block-floodlight-access", HttpMethod.POST,
                new HttpEntity<>(new InetAddress(port), buildJsonHeaders()), String.class);
    }

    @Override
    public void unblockFloodlightAccessToPort(Integer port) {
        log.debug("Unblock floodlight access to {} by removing iptables rules", port);
        restTemplate.exchange(labService.getLab().getLabId() + "/unblock-floodlight-access", HttpMethod.POST,
                new HttpEntity<>(new InetAddress(port), buildJsonHeaders()), String.class);
    }

    @Override
    public void removeFloodlightAccessRestrictions() {
        log.debug("Allow floodlight access to everything by flushing iptables rules(INPUT/OUTPUT chains)");
        restTemplate.exchange(labService.getLab().getLabId() + "/remove-floodlight-access-restrictions",
                HttpMethod.POST, new HttpEntity(buildJsonHeaders()), String.class);
    }

    @Override
    public void knockoutFloodlight() {
        log.debug("Knock out Floodlight service");
        blockFloodlightAccessToPort(Integer.valueOf(kafkaBootstrapServer.get(2)));
    }

    @Override
    public void reviveFloodlight() {
        log.debug("Revive Floodlight service");
        unblockFloodlightAccessToPort(Integer.valueOf(kafkaBootstrapServer.get(2)));
    }

    HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
