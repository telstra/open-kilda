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
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.SwitchModify;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
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
 * Simulates the same functionality as {@link LockKeeperServiceImpl} but for virtual network.
 */
@Service
@Profile("virtual")
@Slf4j
public class LockKeeperVirtualImpl implements LockKeeperService {
    private static final String FL_CONTAINER_NAME = "/floodlight";

    @SuppressWarnings("squid:S1075")
    private static final String FLOWS_PATH = "/flows";

    @Autowired
    @Qualifier("lockKeeperRestTemplate")
    private RestTemplate restTemplate;

    @Value("${floodlight.controller.uri}")
    private String controllerHost;

    private DockerClient dockerClient;
    private Container floodlight;

    public LockKeeperVirtualImpl() throws DockerCertificateException, DockerException, InterruptedException {
        dockerClient = DefaultDockerClient.fromEnv().build();
        floodlight = dockerClient.listContainers().stream().filter(c -> c.names().contains(FL_CONTAINER_NAME))
                .findFirst().orElseThrow(() -> new IllegalStateException("Can't find floodlight container"));
    }

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(FLOWS_PATH, HttpMethod.POST,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        restTemplate.exchange(FLOWS_PATH, HttpMethod.DELETE,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        ASwitchFlow[] flows = restTemplate.exchange(FLOWS_PATH, HttpMethod.GET,
                new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public void portsUp(List<Integer> ports) {
        restTemplate.exchange("/ports", HttpMethod.POST,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought up ports: {}", ports);
    }

    @Override
    public void portsDown(List<Integer> ports) {
        restTemplate.exchange("/ports", HttpMethod.DELETE,
                new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought down ports: {}", ports);
    }

    @Override
    public void knockoutSwitch(Switch switchDef) {
        restTemplate.exchange("/knockoutswitch", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(switchDef.getName(), null), buildJsonHeaders()), String.class);
        log.debug("Knocking out switch: {}", switchDef.getName());
    }

    @Override
    public void reviveSwitch(Switch switchDef) {
        restTemplate.exchange("/reviveswitch", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(switchDef.getName(), controllerHost),
                        buildJsonHeaders()), String.class);
        log.debug("Revive switch: {}", switchDef.getName());
    }

    @Override
    public void stopFloodlight() {
        try {
            dockerClient.stopContainer(floodlight.id(), 5);
        } catch (DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void startFloodlight() {
        try {
            dockerClient.startContainer(floodlight.id());
        } catch (DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void restartFloodlight() {
        try {
            dockerClient.restartContainer(floodlight.id(), 5);
        } catch (DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
