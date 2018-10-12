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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LabServiceImpl implements LabService, DisposableBean {

    @Value("${spring.profiles.active}")
    private String profile;
    @Autowired
    private TopologyDefinition topology;
    @Autowired
    @Qualifier("labApiRestTemplate")
    private RestTemplate restTemplate;

    private LabInstance labInstance;

    private boolean isHwProfile() {
        return profile.equals("hardware");
    }

    @Override
    public synchronized LabInstance getLab() {
        if (labInstance == null) {
            labInstance = createLab();
        }
        return labInstance;
    }

    @Override
    public synchronized void destroy() {
        if (labInstance != null) {
            deleteLab(labInstance);
            labInstance = null;
        }
    }

    private LabInstance createLab() {
        log.info("Creating topology");
        return restTemplate.exchange("/api", HttpMethod.POST,
                new HttpEntity<>(topology, buildJsonHeaders()), LabInstance.class).getBody();
    }

    private void deleteLab(LabInstance lab) {
        log.info("Deleting topology {}", lab.getLabId());
        restTemplate.delete("/api/" + lab.getLabId());
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (isHwProfile()) {
            headers.add("Hw-Env", "HW");
        }
        return headers;
    }
}
