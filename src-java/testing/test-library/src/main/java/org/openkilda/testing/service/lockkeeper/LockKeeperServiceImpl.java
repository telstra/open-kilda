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

import static java.util.stream.Collectors.toList;
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW;

import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.FloodlightsHelper;
import org.openkilda.testing.service.floodlight.model.Floodlight;
import org.openkilda.testing.service.floodlight.model.FloodlightConnectMode;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.ChangeSwIpRequest;
import org.openkilda.testing.service.lockkeeper.model.ContainerName;
import org.openkilda.testing.service.lockkeeper.model.FloodlightResourceAddress;
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData;
import org.openkilda.testing.service.lockkeeper.model.TrafficControlRequest;
import org.openkilda.testing.service.northbound.NorthboundService;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.Map;
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
    @Qualifier("islandNb")
    private NorthboundService northbound;

    @Autowired
    private FloodlightsHelper flHelper;

    @Value("${kafka.bootstrap.server}")
    private String kafkaBootstrapServer;

    /**
     * Each floodlight host has it's own local lock-keeper service deployed in order to have full access to it.
     */
    @Autowired
    @Qualifier("lockKeeperRestTemplates")
    private Map<String, RestTemplate> lockKeepersByRegion;

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        RestTemplate restTemplate = lockKeepersByRegion.values().iterator().next(); //any template fits
        restTemplate.exchange("/flows", HttpMethod.POST, new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        RestTemplate restTemplate = lockKeepersByRegion.values().iterator().next();
        restTemplate.exchange("/flows", HttpMethod.DELETE, new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        RestTemplate restTemplate = lockKeepersByRegion.values().iterator().next();
        ASwitchFlow[] flows = restTemplate.exchange("/flows",
                HttpMethod.GET, new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public void portsUp(List<Integer> ports) {
        RestTemplate restTemplate = lockKeepersByRegion.values().iterator().next();
        restTemplate.exchange("/ports", HttpMethod.POST, new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought up ports: {}", ports);
    }

    @Override
    public void portsDown(List<Integer> ports) {
        RestTemplate restTemplate = lockKeepersByRegion.values().iterator().next();
        restTemplate.exchange("/ports", HttpMethod.DELETE, new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought down ports: {}", ports);
    }

    @Override
    public void stopFloodlight(String region) {
        lockKeepersByRegion.get(region).exchange("/floodlight/stop",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                        buildJsonHeaders()), String.class);
        log.debug("Stopping Floodlight");
    }

    @Override
    public void startFloodlight(String region) {
        lockKeepersByRegion.get(region).exchange("/floodlight/start",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                        buildJsonHeaders()), String.class);
        log.debug("Starting Floodlight");
    }

    @Override
    public void restartFloodlight(String region) {
        lockKeepersByRegion.get(region).exchange("/floodlight/restart",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                        buildJsonHeaders()), String.class);
        log.debug("Restarting Floodlight");
    }

    @Override
    public List<FloodlightResourceAddress> knockoutSwitch(Switch sw, List<String> regions) {
        log.debug("Block Floodlight access to switch '{}' by adding iptables rules", sw.getName());
        List<FloodlightResourceAddress> flResourceAddresses = toFlResources(sw, regions);
        flResourceAddresses.forEach(address ->
                lockKeepersByRegion.get(address.getRegion()).exchange("/floodlight/block-switch", HttpMethod.POST,
                        new HttpEntity<>(address, buildJsonHeaders()), String.class));
        return flResourceAddresses;
    }

    @Override
    public List<FloodlightResourceAddress> knockoutSwitch(Switch sw, FloodlightConnectMode mode) {
        return knockoutSwitch(sw, flHelper.filterRegionsByMode(sw.getRegions(), RW));
    }

    @Override
    public void reviveSwitch(Switch sw, List<FloodlightResourceAddress> flResourceAddresses) {
        log.debug("Unblock Floodlight access to switch '{}' by removing iptables rules", sw.getName());
        flResourceAddresses.forEach(address ->
                lockKeepersByRegion.get(address.getRegion()).exchange("/floodlight/unblock-switch", HttpMethod.POST,
                        new HttpEntity<>(address, buildJsonHeaders()), String.class));
    }

    @Override
    public void setController(Switch sw, String controller) {
        throw new UnsupportedOperationException(
                "setController method is not available on hardware env");
    }

    @Override
    public void blockFloodlightAccess(FloodlightResourceAddress address) {
        log.debug("Block floodlight access to {} by adding iptables rules", address);
        lockKeepersByRegion.get(address.getRegion()).exchange("/floodlight/block",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void unblockFloodlightAccess(FloodlightResourceAddress address) {
        log.debug("Unblock floodlight access to {} by removing iptables rules", address);
        lockKeepersByRegion.get(address.getRegion()).exchange("/floodlight/unblock",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void shapeSwitchesTraffic(List<Switch> switches, TrafficControlData tcData) {
        log.debug("Add traffic control rules for switches {}",
                switches.stream().map(Switch::getDpId).collect(toList()));
        switches.stream().flatMap(sw -> toFlResources(sw, sw.getRegions()).stream())
                .collect(Collectors.groupingBy(FloodlightResourceAddress::getRegion)).entrySet().parallelStream()
                .forEach(resourcesPerRegion -> lockKeepersByRegion.get(resourcesPerRegion.getKey())
                        .exchange("/floodlight/tc", HttpMethod.POST,
                                new HttpEntity<>(new TrafficControlRequest(tcData, resourcesPerRegion.getValue()),
                                        buildJsonHeaders()), String.class));
    }

    @Override
    public void cleanupTrafficShaperRules(List<String> regions) {
        regions.forEach(region -> {
            log.debug("Cleanup traffic control rules for region {}", region);
            lockKeepersByRegion.get(region).exchange("/floodlight/tc/cleanup", HttpMethod.POST,
                    new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                            buildJsonHeaders()), String.class);
        });
    }

    @Override
    public void removeFloodlightAccessRestrictions(List<String> regions) {
        log.debug("Allow floodlight access to everything by flushing iptables rules(INPUT/OUTPUT chains)");
        regions.forEach(region -> {
            lockKeepersByRegion.get(region).exchange("/floodlight/unblock-all", HttpMethod.POST,
                    new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                            buildJsonHeaders()), String.class);
        });
    }

    @Override
    public void knockoutFloodlight(String region) {
        log.debug("Knock out Floodlight service");
        blockFloodlightAccess(new FloodlightResourceAddress(region, flHelper.getFlByRegion(region).getContainer(),
                getPort(kafkaBootstrapServer)));
    }

    @Override
    public void reviveFloodlight(String region) {
        log.debug("Revive Floodlight service");
        unblockFloodlightAccess(new FloodlightResourceAddress(region, flHelper.getFlByRegion(region).getContainer(),
                getPort(kafkaBootstrapServer)));
    }

    @Override
    public void updateBurstSizeAndRate(Switch sw, Long meterId, Long burstSize, Long rate) {
        throw new UnsupportedOperationException(
                "updateBurstSizeAndRate method is not available on hardware env");
    }

    @Override
    public void changeSwIp(String region, String oldIp, String newIp) {
        log.debug("Change sw ip from {} to {} for region {}", oldIp, newIp, region);
        lockKeepersByRegion.get(region).exchange("/floodlight/nat/input", HttpMethod.POST,
                new HttpEntity<>(new ChangeSwIpRequest(region, flHelper.getFlByRegion(region).getContainer(),
                        oldIp, newIp)), String.class);
    }

    @Override
    public void cleanupIpChanges(String region) {
        log.debug("Flush NAT INPUT chain for region {}", region);
        lockKeepersByRegion.get(region).exchange("/floodlight/nat/input/flush", HttpMethod.POST,
                new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer())), String.class);
    }

    @Override
    public void setLinkDelay(String bridgeName, Integer delayMs) {
        throw new UnsupportedOperationException("setLinkDelay method is not available on hardware env");
    }

    @Override
    public void cleanupLinkDelay(String bridgeName) {
        throw new UnsupportedOperationException("cleanupLinkDelay method is not available on hardware env");
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


    private List<FloodlightResourceAddress> toFlResources(Switch sw, List<String> regions) {
        return sw.getRegions().stream().filter(regions::contains).map(region -> {
            Floodlight fl = flHelper.getFlByRegion(region);
            String swAddress = fl.getFloodlightService().getSwitches().stream().filter(s ->
                    sw.getDpId().equals(s.getSwitchId())).findFirst().get().getAddress();
            Pair<String, Integer> inetAddress = LockKeeperService.parseAddressPort(swAddress);
            return new FloodlightResourceAddress(region, fl.getContainer(), inetAddress.getLeft(),
                    inetAddress.getRight());
        }).collect(toList());
    }
}
