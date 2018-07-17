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

package org.openkilda.testlib.service.floodlight;

import static java.lang.String.format;

import org.openkilda.testlib.model.controller.CoreFlowEntry;
import org.openkilda.testlib.model.controller.DpIdEntriesList;
import org.openkilda.testlib.model.controller.StaticFlowEntry;
import org.openkilda.testlib.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testlib.service.floodlight.model.MetersEntriesMap;
import org.openkilda.testlib.service.floodlight.model.SwitchEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class FloodlightServiceImpl implements FloodlightService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FloodlightServiceImpl.class);

    @Autowired
    @Qualifier("floodlightRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public String addStaticFlow(StaticFlowEntry flow) {
        return restTemplate.postForObject("/wm/staticentrypusher/json", flow, String.class);
    }

    @Override
    public String getAliveStatus() {
        return restTemplate.getForObject("/wm/core/controller/summary/json", String.class);
    }

    @Override
    public List<CoreFlowEntry> getCoreFlows(String dpId) {
        CoreFlowEntry[] coreFlows = restTemplate.getForObject("/wm/core/switch/{dp_id}/flow/json",
                CoreFlowEntry[].class, dpId);
        return Arrays.asList(coreFlows);
    }

    @Override
    public DpIdEntriesList getStaticEntries(String dpId) {
        return restTemplate.getForObject("/wm/staticentrypusher/list/{dp_id}/json",
                DpIdEntriesList.class, dpId);
    }

    @Override
    public List<SwitchEntry> getSwitches() {
        SwitchEntry[] result = restTemplate.getForObject("/wm/core/controller/switches/json", SwitchEntry[].class);
        return Arrays.asList(result);
    }

    @Override
    public FlowEntriesMap getFlows(String dpid) {
        return restTemplate.getForObject("/wm/kilda/flows/switch_id/{switch_id}", FlowEntriesMap.class, dpid);
    }

    @Override
    public MetersEntriesMap getMeters(String dpid) {
        try {
            return restTemplate.getForObject("/wm/kilda/meters/switch_id/{switch_id}", MetersEntriesMap.class, dpid);
        } catch (HttpServerErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_IMPLEMENTED) {
                throw new UnsupportedOperationException(format("Switch %s doesn't support dumping of meters.", dpid),
                        ex);
            }

            throw ex;
        }
    }
}
