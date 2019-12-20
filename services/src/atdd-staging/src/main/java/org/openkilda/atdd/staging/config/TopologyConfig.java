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

import org.openkilda.testing.model.topology.TopologyDefinition;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class TopologyConfig {

    @Value("file:${topology.definition.file:topology.yaml}")
    private Resource topologyDefinitionFile;

    @Value("#{'${floodlight.controllers.management.openflow}'.split(',').get(0)}")
    private String managementController;

    @Value("#{'${floodlight.controllers.stat.openflow}'.split(',').get(0)}")
    private String statController;

    @Value("${bfd.offset}")
    private Integer bfdOffset;

    @Bean
    public TopologyDefinition topologyDefinition() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

        TopologyDefinition topologyDefinition =
                mapper.readValue(topologyDefinitionFile.getInputStream(), TopologyDefinition.class);

        List<String> controllerHosts = Arrays.asList(managementController, statController);

        topologyDefinition.setBfdOffset(bfdOffset);
        topologyDefinition.setControllers(controllerHosts);
        for (TopologyDefinition.Switch sw : topologyDefinition.getSwitches()) {
            sw.setController(managementController + " " + statController);
        }
        return topologyDefinition;
    }
}
