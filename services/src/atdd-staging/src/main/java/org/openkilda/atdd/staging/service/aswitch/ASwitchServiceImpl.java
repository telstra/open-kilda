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

package org.openkilda.atdd.staging.service.aswitch;

import org.openkilda.atdd.staging.service.aswitch.model.ASwitchFlow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * This service takes control over the intermediate 'A Switch' in Staging which is meant to allow to
 * disconnect ISLs.
 */
@Service
public class ASwitchServiceImpl implements ASwitchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ASwitchServiceImpl.class);

    @Autowired
    @Qualifier("aSwitchRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public void addFlow(List<ASwitchFlow> flows) {
        restTemplate.exchange("/flows", HttpMethod.POST,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
    }

    @Override
    public void removeFlow(List<ASwitchFlow> flows) {
        restTemplate.exchange("/flows", HttpMethod.DELETE,
                new HttpEntity<>(flows, buildJsonHeaders()), String.class);
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        ASwitchFlow[] flows = restTemplate.exchange("/flows", HttpMethod.GET,
                new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
