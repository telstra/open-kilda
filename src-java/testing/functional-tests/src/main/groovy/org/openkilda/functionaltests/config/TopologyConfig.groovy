/* Copyright 2019 Telstra Open Source
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

package org.openkilda.functionaltests.config

import org.openkilda.testing.model.topology.TopologyDefinition

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TopologyConfig {

    @Value('${topology.definition.file:}')
    private String topologyDefinitionFileLocation

    @Value("#{'\${floodlight.regions}'.split(',')}")
    List<String> regions

    @Value("#{'\${floodlight.controllers.management.openflow}'.split(',')}")
    List<String> managementControllers

    @Value("#{'\${floodlight.controllers.stat.openflow}'.split(',')}")
    List<String> statControllers

    @Value('${bfd.offset}')
    private Integer bfdOffset

    private File getTopologyDefinitionFile() {
        if(StringUtils.isNotEmpty(topologyDefinitionFileLocation)) {
            return new File(topologyDefinitionFileLocation)
        } else if(new File("topology.yaml").exists()){
            return new File("topology.yaml")
        } else {
            return new File("src/test/resources/topology.yaml")
        }
    }


    @Bean
    TopologyDefinition topologyDefinition() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        TopologyDefinition topologyDefinition =
                mapper.readValue(FileUtils.openInputStream(getTopologyDefinitionFile()), TopologyDefinition.class)

        topologyDefinition.setControllers([managementControllers[0], statControllers[0]])
        topologyDefinition.setBfdOffset(bfdOffset)
        topologyDefinition.switches.each { sw ->
            def regionIndex = regions.indexOf(sw.getRegion())
            if(regionIndex == -1) {
                throw new RuntimeException("Switch $sw has an unknown region '${sw.getRegion()}'. All regions should" +
                        "be specified in kilda.properties.")
            }
            sw.setController(managementControllers[regionIndex] + " " + statControllers[regionIndex])
        }
        return topologyDefinition
    }
}
