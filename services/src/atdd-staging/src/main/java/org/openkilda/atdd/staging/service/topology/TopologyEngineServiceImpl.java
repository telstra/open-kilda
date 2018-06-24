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

package org.openkilda.atdd.staging.service.topology;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.topo.ITopology;
import org.openkilda.topo.builders.TeTopologyParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class TopologyEngineServiceImpl implements TopologyEngineService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopologyEngineServiceImpl.class);

    @Autowired
    @Qualifier("topologyEngineRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public Integer getLinkBandwidth(String srcSwitch, String srcPort) {
        return restTemplate.getForObject("/api/v1/topology/links/bandwidth/{src_switch}/{src_port}", Integer.class,
                srcSwitch, srcPort);
    }

    @Override
    public List<Flow> getAllFlows() {
        Flow[] links = restTemplate.getForObject("/api/v1/topology/flows", Flow[].class);
        return Arrays.asList(links);
    }

    @Override
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        try {
            return restTemplate.exchange("/api/v1/topology/flows/{flow_id}", HttpMethod.GET, null,
                    new ParameterizedTypeReference<ImmutablePair<Flow, Flow>>() {
                    }, flowId).getBody();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }

            return null;
        }
    }

    @Override
    public void restoreFlows() {
        restTemplate.getForObject("/api/v1/flows/restore", String.class);
    }

    @Override
    public ITopology getTopology() {
        String topologyJson = restTemplate.getForObject("/api/v1/topology/network", String.class);
        return TeTopologyParser.parseTopologyEngineJson(topologyJson);
    }

    @Override
    public String clearTopology() {
        return restTemplate.getForObject("/api/v1/topology/clear", String.class);
    }

    @Override
    public List<PathInfoData> getPaths(String srcSwitch, String dstSwitch) {
        PathInfoData[] paths = restTemplate
                .getForObject("/api/v1/topology/routes/src/{src_switch}/dst/{dst_switch}",
                        PathInfoData[].class, srcSwitch, dstSwitch);
        return Arrays.asList(paths);
    }
}
