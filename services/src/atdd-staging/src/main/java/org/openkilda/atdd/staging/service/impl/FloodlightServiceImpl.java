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

package org.openkilda.atdd.staging.service.impl;

import org.openkilda.atdd.staging.service.FloodlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FloodlightServiceImpl implements FloodlightService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FloodlightServiceImpl.class);

    @Autowired
    @Qualifier("floodlightRestTemplate")
    private RestTemplate restTemplate;

//    public String addStaticFlow(StaticFlowEntry flow) {
//        return restTemplate.postForObject("/wm/staticentrypusher/jso", flow, String.class);
//    }

    @Override
    public String getAliveStatus() {
        return restTemplate.getForObject("/wm/core/controller/summary/json", String.class);
    }

//    public List<CoreFlowEntry> getCoreFlows(String dpId) {
//        Map<String, Object> params = new HashMap<>();
//        params.put("dp_id", dpId);
//
//        CoreFlowEntry[] coreFlows = restTemplate.getForObject("/wm/core/switch/{dp_id}/flow/json",
//                CoreFlowEntry[].class, params);
//        return Arrays.asList(coreFlows);
//    }

//    public DpIdEntriesList getStaticEntries(String dpId) {
//        Map<String, Object> params = new HashMap<>();
//        params.put("dp_id", dpId);
//
//        return restTemplate.getForObject("/wm/staticentrypusher/list/{dp_id}/json",
//                DpIdEntriesList.class, params);
//    }
}
