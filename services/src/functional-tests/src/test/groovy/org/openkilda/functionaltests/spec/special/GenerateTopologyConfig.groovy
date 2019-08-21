package org.openkilda.functionaltests.spec.special

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Status
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGenConfig

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature

/**
 * Generates a topology.yaml file based on what is currently discovered in Kilda.
 * Topology.yaml file is required to run any test and one may want to review the generated file and update it manually.
 */
class GenerateTopologyConfig extends BaseSpecification {

    def "Generate 'topology.yaml' file based on currently discovered topology"() {
        when: "Build topology config based on existing switches and ISLs"
        def topo = topologyHelper.readCurrentTopology()

        then: "Able to serialize and save it to 'target'"
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().configure(Feature.USE_NATIVE_OBJECT_ID, false))
        mapper.setSerializationInclusion(Include.NON_NULL)
        new File("target/topology.yaml").write(mapper.writeValueAsString(topo))
    }
}
