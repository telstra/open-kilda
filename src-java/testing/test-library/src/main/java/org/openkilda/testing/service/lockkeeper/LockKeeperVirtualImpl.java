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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.FloodlightsHelper;
import org.openkilda.testing.service.floodlight.model.Floodlight;
import org.openkilda.testing.service.floodlight.model.FloodlightConnectMode;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.ChangeSwIpRequest;
import org.openkilda.testing.service.lockkeeper.model.ContainerName;
import org.openkilda.testing.service.lockkeeper.model.FloodlightResourceAddress;
import org.openkilda.testing.service.lockkeeper.model.LinkDelayModify;
import org.openkilda.testing.service.lockkeeper.model.MeterModify;
import org.openkilda.testing.service.lockkeeper.model.SwitchModify;
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData;
import org.openkilda.testing.service.lockkeeper.model.TrafficControlRequest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * Provide functionality of {@link LockKeeperService} for virtual network.
 */
@Slf4j
@Service
@Profile("virtual")
@Scope(SCOPE_PROTOTYPE)
public class LockKeeperVirtualImpl implements LockKeeperService {

    public static final String DUMMY_CONTROLLER = "tcp:192.0.2.0:6666";

    @Autowired
    TopologyDefinition topology;
    @Autowired
    private FloodlightsHelper flHelper;

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
     * Disconnects certain switch from specified floodlight group (management/statistic) by re-setting controllers
     * on switch. If switch is connected to multiple regions, remove it from all those regions.
     * !Important note: having a knocked-out switch will disable new virtual switches from being connected until iptable
     * changes are reverted.
     */
    @Override
    public List<FloodlightResourceAddress> knockoutSwitch(Switch sw, List<String> regions) {
        String[] currentControllers = regions.stream().map(r -> flHelper.getFlByRegion(r).getOpenflow())
                .toArray(String[]::new);
        String[] replacements = new String[currentControllers.length];
        Arrays.fill(replacements, DUMMY_CONTROLLER);
        sw.setController(StringUtils.replaceEach(sw.getController(), currentControllers, replacements));
        setController(sw, sw.getController());
        return regions.stream().map(region ->
                new FloodlightResourceAddress(region, flHelper.getFlByRegion(region).getContainer(), ""))
                .collect(toList());
    }

    @Override
    public List<FloodlightResourceAddress> knockoutSwitch(Switch sw, FloodlightConnectMode mode) {
        return knockoutSwitch(sw, flHelper.filterRegionsByMode(sw.getRegions(), mode));
    }

    @Override
    public void reviveSwitch(Switch sw, List<FloodlightResourceAddress> flResourceAddresses) {
        List<String> containerNames = flResourceAddresses.stream().map(FloodlightResourceAddress::getContainerName)
                .distinct().collect(toList());
        List<String> requiredControllers = containerNames.stream().map(name ->
                flHelper.getFlByContainer(name).getOpenflow()).collect(toList());
        String newController = sw.getController();
        for (String controller : requiredControllers) {
            newController = newController.replaceFirst(DUMMY_CONTROLLER, controller);
        }
        sw.setController(newController);
        setController(sw, sw.getController());
    }

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/flows", HttpMethod.POST,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Added flows: {}", flows.stream()
                .map(flow -> format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/flows", HttpMethod.DELETE,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Removed flows: {}", flows.stream()
                .map(flow -> format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(toList()));
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
                HttpMethod.POST, new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                        buildJsonHeaders()), String.class);
        log.debug("Stopping Floodlight");
    }

    @Override
    public void startFloodlight(String region) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/start",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                        buildJsonHeaders()), String.class);
        log.debug("Starting Floodlight");
    }

    @Override
    public void restartFloodlight(String region) {
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/restart",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                        buildJsonHeaders()), String.class);
        log.debug("Restarting Floodlight");
    }

    @Override
    public void blockFloodlightAccess(FloodlightResourceAddress address) {
        log.debug("Block floodlight access to {} by adding iptables rules", address);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/block",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void unblockFloodlightAccess(FloodlightResourceAddress address) {
        log.debug("Unblock floodlight access to {} by removing iptables rules", address);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/unblock",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void shapeSwitchesTraffic(List<Switch> switches, TrafficControlData tcData) {
        log.debug("Add traffic control rules for switches {}",
                switches.stream().map(Switch::getDpId).collect(toList()));
        List<FloodlightResourceAddress> swResources = switches.stream()
                .flatMap(sw -> sw.getRegions().stream().map(region -> {
                    Floodlight fl = flHelper.getFlByRegion(region);
                    String swAddress = fl.getFloodlightService().getSwitches().stream().filter(s ->
                            sw.getDpId().equals(s.getSwitchId())).findFirst().get().getAddress();
                    Pair<String, Integer> inetAddress = LockKeeperService.parseAddressPort(swAddress);
                    return new FloodlightResourceAddress(region, fl.getContainer(), inetAddress.getLeft(),
                            inetAddress.getRight());
                })).collect(toList());
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/tc", HttpMethod.POST,
                new HttpEntity<>(new TrafficControlRequest(tcData, swResources), buildJsonHeaders()), String.class);
    }

    @Override
    public void cleanupTrafficShaperRules(List<String> regions) {
        regions.forEach(region -> {
            log.debug("Cleanup traffic control rules for region {}", region);
            restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/tc/cleanup", HttpMethod.POST,
                    new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                            buildJsonHeaders()), String.class);
        });
    }

    @Override
    public void removeFloodlightAccessRestrictions(List<String> regions) {
        log.debug("Allow floodlight access to everything by flushing iptables rules(INPUT/OUTPUT chains)");
        regions.forEach(region -> {
            restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/unblock-all", HttpMethod.POST,
                    new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer()),
                            buildJsonHeaders()), String.class);
        });
    }

    @Override
    public void updateBurstSizeAndRate(Switch sw, Long meterId, Long burstSize, Long rate) {
        log.debug("Update meterId: '{}', burstSize: '{}' and rate: '{}' on sw: '{}'", meterId, burstSize, rate,
                sw.getName());
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/meter/update", HttpMethod.POST,
                new HttpEntity<>(new MeterModify(sw.getName(), meterId, burstSize, rate), buildJsonHeaders()),
                String.class);
    }

    @Override
    public void knockoutFloodlight(String region) {
        log.debug(format("Knock out Floodlight service region %s", region));
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
    public void changeSwIp(String region, String oldIp, String newIp) {
        log.debug("Change sw ip from {} to {} for region {}", oldIp, newIp, region);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/nat/input", HttpMethod.POST,
                new HttpEntity<>(new ChangeSwIpRequest(region, flHelper.getFlByRegion(region).getContainer(),
                        oldIp, newIp)), String.class);
    }

    @Override
    public void cleanupIpChanges(String region) {
        log.debug("Flush NAT INPUT chain for region {}", region);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/floodlight/nat/input/flush", HttpMethod.POST,
                new HttpEntity<>(new ContainerName(flHelper.getFlByRegion(region).getContainer())), String.class);
    }

    @Override
    public void setLinkDelay(String bridgeName, Integer delayMs) {
        log.debug("Set link delay: interface: '{}', delayMs: '{}'.", bridgeName, delayMs);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/link/delay", HttpMethod.POST,
                new HttpEntity<>(new LinkDelayModify(bridgeName, delayMs), buildJsonHeaders()), String.class);
    }

    @Override
    public void cleanupLinkDelay(String bridgeName) {
        log.debug("Cleanup link delay: interface: '{}'.", bridgeName);
        restTemplate.exchange(getCurrentLabUrl() + "/lock-keeper/link/delay/cleanup", HttpMethod.POST,
                new HttpEntity<>(new LinkDelayModify(bridgeName, null), buildJsonHeaders()), String.class);
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
        return "api/" + topology.getLabId();
    }
}
