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
        when: "Request discovered switches and ISLs from northbound"
        def switches = northbound.getAllSwitches()
        def links = northbound.getAllLinks()

        and: "Build topology config based on this data"
        def i = 0
        def topoSwitches = switches.collect {
            i++
            new Switch("ofsw$i", it.switchId, it.switchView.ofVersion, switchStateToStatus(it.state), [], null, null)
        }
        def topoLinks = links.collect { link ->
            new Isl(topoSwitches.find { it.dpId == link.source.switchId }, link.source.portNo,
                    topoSwitches.find { it.dpId == link.destination.switchId }, link.destination.portNo,
                    link.maxBandwidth, null)
        }
        def topo = new TopologyDefinition(topoSwitches, topoLinks, [], TraffGenConfig.defaultConfig())

        then: "Able to serialize and save it to 'target'"
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().configure(Feature.USE_NATIVE_OBJECT_ID, false))
        mapper.setSerializationInclusion(Include.NON_NULL)
        new File("target/topology.yaml").write(mapper.writeValueAsString(topo))
    }

    Status switchStateToStatus(SwitchChangeType state) {
        switch (state) {
            case SwitchChangeType.ACTIVATED:
                return Status.Active
            default:
                return Status.Inactive
        }
    }
}
