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

package org.openkilda.testing.service.labservice;

import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.service.labservice.model.LabInstance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LabServiceImpl implements LabService, DisposableBean {
    @Autowired
    @Qualifier("labApiRestTemplate")
    private RestTemplate restTemplate;
    @Value("${spring.profiles.active}")
    private String profile;

    private LabInstance currentLab;

    @Override
    public synchronized List<Long> flushLabs() {
        log.info("Flushing all labs");
        return restTemplate.exchange("/api/flush", HttpMethod.POST,
                new HttpEntity(buildJsonHeaders()), new ParameterizedTypeReference<List<Long>>() {
                }).getBody();
    }

    @Override
    public LabInstance getLab() {
        return currentLab;
    }

    @Override
    public LabInstance createLab(TopologyDefinition topology) {
        log.info("Creating lab with deploying virtual topology");
        currentLab = restTemplate.exchange("/api", HttpMethod.POST,
                new HttpEntity<>(topology, buildJsonHeaders()), LabInstance.class).getBody();
        return currentLab;
    }

    @Override
    public LabInstance createHwLab(TopologyDefinition topology) {
        log.info("Creating lab with redirecting to hardware topology");
        HttpHeaders headers = buildJsonHeaders();
        headers.add("Hw-Env", "HW");
        currentLab = restTemplate.exchange("/api", HttpMethod.POST,
                new HttpEntity<>(topology, headers), LabInstance.class).getBody();
        return currentLab;
    }

    @Override
    public List<LabInstance> getLabs() {
        log.info("Get live labs");
        return restTemplate.exchange("/api", HttpMethod.GET,
                new HttpEntity(buildJsonHeaders()), new ParameterizedTypeReference<List<Long>>() {
                }).getBody().stream().map(LabInstance::new).collect(Collectors.toList());
    }

    @Override
    public void deleteLab(LabInstance lab) {
        log.info("Deleting topology {}", lab.getLabId());
        restTemplate.delete("/api/" + lab.getLabId());
        if (lab.getLabId().equals(currentLab.getLabId())) {
            currentLab = null;
        }
    }

    @Override
    public void deleteLab() {
        deleteLab(currentLab);
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    @Override
    public void destroy() throws Exception {
        if ("hardware".equals(profile)) {
            deleteLab();
        }
    }
}
