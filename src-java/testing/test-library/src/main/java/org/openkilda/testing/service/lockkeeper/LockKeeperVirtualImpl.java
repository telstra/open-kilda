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
import org.openkilda.testing.service.floodlight.ManagementFloodlightManager;
import org.openkilda.testing.service.floodlight.MultiFloodlightManager;
import org.openkilda.testing.service.floodlight.StatsFloodlightManager;
import org.openkilda.testing.service.labservice.LabService;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.ContainerName;
import org.openkilda.testing.service.lockkeeper.model.FloodlightResourceAddress;
import org.openkilda.testing.service.lockkeeper.model.SwitchModify;
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData;
import org.openkilda.testing.service.lockkeeper.model.TrafficControlRequest;
import org.openkilda.testing.service.northbound.NorthboundService;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.stream.Stream;

/**
 * Provide functionality of {@link LockKeeperService} for virtual network.
 */
@Slf4j
@Service
@Profile("virtual")
public class LockKeeperVirtualImpl implements LockKeeperService {

    public static final String DUMMY_CONTROLLER = "tcp:192.0.2.0:6666";

    @Autowired
    LabService labService;
    @Autowired
    private NorthboundService northbound;
    @Autowired
    private ManagementFloodlightManager mgmtManager;
    @Autowired
    private StatsFloodlightManager statsManager;

    @Value("${kafka.bootstrap.server}")
    private String kafkaBootstrapServer;

    @Autowired
    @Qualifier("labApiRestTemplate")
    protected RestTemplate restTemplate;

    @Override
    public void setController(Switch sw, String controller) {
        log.debug("Set '{}' controller on the '{}' switch", controller, sw.getName());
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/set-controller", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(sw.getName(), controller), buildJsonHeaders()), String.class);
    }

    /**
     * Disconnects certain switch from specified floodlight by adding iptable rules to FL container.
     * !Important note: having a knocked-out switch will disable new virtual switches from being connected until iptable
     * changes are reverted.
     */
    @Override
    public FloodlightResourceAddress knockoutSwitch(Switch sw, MultiFloodlightManager manager) {
        String containerName = manager.getContainerName(sw.getRegion());
        String currentController = manager.getControllerAddresses().get(
                manager.getContainerNames().indexOf(containerName));
        sw.setController(StringUtils.replaceOnce(sw.getController(), currentController, DUMMY_CONTROLLER));
        setController(sw, sw.getController());
        return new FloodlightResourceAddress(containerName, "");
    }

    @Override
    public void reviveSwitch(Switch sw, FloodlightResourceAddress flResourceAddress) {
        MultiFloodlightManager manager = Stream.of(mgmtManager, statsManager).filter(m ->
                m.getContainerNames().indexOf(flResourceAddress.getContainerName()) > -1).findFirst().get();
        String currentController = manager.getControllerAddresses().get(
                manager.getContainerNames().indexOf(flResourceAddress.getContainerName()));
        sw.setController(StringUtils.replaceOnce(sw.getController(), DUMMY_CONTROLLER, currentController));
        setController(sw, sw.getController());
    }

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/flows", HttpMethod.POST,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/flows", HttpMethod.DELETE,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        ASwitchFlow[] flows = restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/flows",
                HttpMethod.GET, new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public void portsUp(List<Integer> ports) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/ports", HttpMethod.POST,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought up ports: {}", ports);
    }

    @Override
    public void portsDown(List<Integer> ports) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/ports", HttpMethod.DELETE,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought down ports: {}", ports);
    }

    @Override
    public void stopFloodlight(String region) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/stop",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(mgmtManager.getContainerName(region)),
                        buildJsonHeaders()), String.class);
        log.debug("Stopping Floodlight");
    }

    @Override
    public void startFloodlight(String region) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/start",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(mgmtManager.getContainerName(region)),
                        buildJsonHeaders()), String.class);
        log.debug("Starting Floodlight");
    }

    @Override
    public void restartFloodlight(String region) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/restart",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(mgmtManager.getContainerName(region)),
                        buildJsonHeaders()), String.class);
        log.debug("Restarting Floodlight");
    }

    @Override
    public void blockFloodlightAccess(String region, FloodlightResourceAddress address) {
        log.debug("Block floodlight access to {} by adding iptables rules", address);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/block",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void unblockFloodlightAccess(String region, FloodlightResourceAddress address) {
        log.debug("Unblock floodlight access to {} by removing iptables rules", address);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/unblock",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void shapeSwitchesTraffic(List<Switch> switches, TrafficControlData tcData) {
        log.debug("Add traffic control rules for switches {}",
                switches.stream().map(Switch::getDpId).collect(Collectors.toList()));
        List<FloodlightResourceAddress> swResources = switches.stream()
                .map(sw -> LockKeeperService.toFlResource(sw, mgmtManager)).collect(Collectors.toList());
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/tc", HttpMethod.POST,
                new HttpEntity<>(new TrafficControlRequest(tcData, swResources), buildJsonHeaders()), String.class);
    }

    @Override
    public void cleanupTrafficShaperRules(String region) {
        log.debug("Cleanup traffic control rules for region {}", region);
        String containerName = mgmtManager.getContainerName(region);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/tc/cleanup",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(containerName), buildJsonHeaders()), String.class);
    }

    @Override
    public void removeFloodlightAccessRestrictions(String region) {
        log.debug("Allow floodlight access to everything by flushing iptables rules(INPUT/OUTPUT chains)");
        String containerName = mgmtManager.getContainerName(region);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/unblock-all",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(containerName), buildJsonHeaders()), String.class);
    }

    @Override
    public void knockoutFloodlight(String region) {
        log.debug("Knock out Floodlight service");
        String containerName = mgmtManager.getContainerName(region);
        blockFloodlightAccess(region, new FloodlightResourceAddress(containerName, getPort(kafkaBootstrapServer)));
    }

    @Override
    public void reviveFloodlight(String region) {
        log.debug("Revive Floodlight service");
        String containerName = mgmtManager.getContainerName(region);
        unblockFloodlightAccess(region, new FloodlightResourceAddress(containerName, getPort(kafkaBootstrapServer)));
    }

    HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private Integer getPort(String uri) {
        String[] parts = uri.split(":");
        return Integer.valueOf(parts[parts.length - 1]);
    }

    private String getCurrentLabUrl() {
        return "api/" + labService.getLab().getLabId();
    }
}
