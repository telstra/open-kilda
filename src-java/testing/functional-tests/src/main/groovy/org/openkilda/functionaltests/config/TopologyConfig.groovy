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

import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.tools.TopologyPool

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
@Slf4j
class TopologyConfig {

    @Value('${parallel.topologies:1}')
    private int parallelTopologies
    @Value('${spring.profiles.active}')
    String profile
    @Value('${topology.definition.file:}')
    private String topologyDefinitionFileLocation
    @Value('${bfd.offset}')
    private Integer bfdOffset
    @Value('#{\'${floodlight.openflows}\'.split(\',\')}')
    List<String> openflows
    @Value('#{\'${floodlight.regions}\'.split(\',\')}')
    List<String> regions

    @Bean
    TopologyPool topologyPool() throws IOException {
        if (profile == "hardware") {
            parallelTopologies = 1
        }
        return new TopologyPool((1..parallelTopologies).collect { buildTopologyDefinition(it - 1) })
    }

    @Bean
    @Scope("specThread")
    TopologyDefinition getTopologyDefinition(TopologyPool topologyPool) throws IOException {
        return topologyPool.take()
    }

    /**
     * Read topology.yaml example from file. If parallelism is enabled, copy the example required amount of times and
     * update switchIds and s42 vlans based on island's index.
     */
    TopologyDefinition buildTopologyDefinition(int index) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        TopologyDefinition topologyDefinition =
                mapper.readValue(FileUtils.openInputStream(getTopologyDefinitionFile()), TopologyDefinition.class)
        topologyDefinition.setBfdOffset(bfdOffset)
        topologyDefinition.switches.each { sw ->
            def controllers = sw.regions.collect {region ->
                openflows[regions.indexOf(region)]
            }
            sw.setController(controllers.join(" "))
            sw.setDpId(new SwitchId(sw.dpId.toLong() + 0x01_00_00 * index))
            if (sw.prop?.server42Vlan) {
                sw.prop.server42Vlan += 100 * index
                assert sw.prop.server42Vlan < 4096, "s42 vlan is bigger than 4095 after parallelism increment"
            }
        }
        return topologyDefinition
    }

    private File getTopologyDefinitionFile() {
        if(StringUtils.isNotEmpty(topologyDefinitionFileLocation)) {
            return new File(topologyDefinitionFileLocation)
        } else if(new File("topology.yaml").exists()){
            return new File("topology.yaml")
        } else {
            return new File("src/test/resources/topology.yaml")
        }
    }
}
