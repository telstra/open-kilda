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

package org.openkilda.atdd.staging.config;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.StubServiceFactory;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.service.traffexam.TraffExamService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mock")
public class StubServiceConfig {

    @Bean
    public StubServiceFactory stubServiceFactory(TopologyDefinition topologyDefinition) {
        return new StubServiceFactory(topologyDefinition);
    }

    @Bean
    public FloodlightService floodlightService(StubServiceFactory factory) {
        return factory.getFloodlightStub();
    }

    @Bean
    public NorthboundService northboundService(StubServiceFactory factory) {
        return factory.getNorthboundStub();
    }

    @Bean
    public TopologyEngineService topologyEngineService(StubServiceFactory factory) {
        return factory.getTopologyEngineStub();
    }

    @Bean
    public TraffExamService traffExamService(StubServiceFactory factory) {
        return factory.getTraffExamStub();
    }
}
