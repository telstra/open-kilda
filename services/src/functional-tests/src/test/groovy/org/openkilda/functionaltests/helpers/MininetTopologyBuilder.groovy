package org.openkilda.functionaltests.helpers

import static org.openkilda.testing.Constants.ASWITCH_NAME

import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchState
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.aswitch.ASwitchService
import org.openkilda.testing.service.aswitch.model.ASwitchFlow
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.mininet.Mininet
import org.openkilda.testing.service.northbound.NorthboundService

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MininetTopologyBuilder {
    @Autowired
    ASwitchService aswitch
    @Autowired
    Mininet mininet
    @Autowired
    TopologyDefinition topologyDefinition
    @Autowired
    Database db
    @Autowired
    NorthboundService northbound

    void buildVirtualEnvironment() {
        //build a mininet topology based on topology.yaml
        def mininetTopology = buildTopology()
        mininet.createTopology(mininetTopology)

        //make correct a-switch
        mininet.knockoutSwitch(ASWITCH_NAME)
        topologyDefinition.islsForActiveSwitches.each { isl ->
            if (isl.aswitch) {
                aswitch.addFlows([new ASwitchFlow(isl.aswitch.inPort, isl.aswitch.outPort),
                                  new ASwitchFlow(isl.aswitch.outPort, isl.aswitch.inPort)])
            }
        }
        //turn on all features
        def features = northbound.getFeatureToggles()
        features.metaClass.properties.each {
            if (it.type == Boolean.class) {
                features.metaClass.setAttribute(features, it.name, true)
            }
        }
        northbound.toggleFeature(features)

        //wait until topology is discovered
        assert Wrappers.wait(120) {
            northbound.getAllLinks().findAll {
                it.state == IslChangeType.DISCOVERED
            }.size() == topologyDefinition.islsForActiveSwitches.size() * 2
        }
        assert Wrappers.wait(3) {
            northbound.getAllSwitches().findAll {
                it.state == SwitchState.ACTIVATED
            }.size() == topologyDefinition.activeSwitches.size()
        }

        //remove any garbage left after configuring a-switch
        db.removeInactiveIsls()
        db.removeInactiveSwitches()
    }

    /**
     * Build a mininet-format topology based on the current topology.yaml file.
     */
    def buildTopology() {
        def topology = new ObjectMapper().readValue(getClass().getResource('/mininet_topology_template.json'), Map)
        topology.switches << [dpid: "0000000000000000", name: ASWITCH_NAME]
        topologyDefinition.activeSwitches.each {
            topology.switches << [dpid: it.dpId.toString().replaceAll(":", ""), name: it.name]
        }
        topologyDefinition.islsForActiveSwitches.each { isl ->
            if (isl.aswitch) {
                topology.links << [node1: isl.srcSwitch.name, node1_port: isl.srcPort,
                                   node2: ASWITCH_NAME, node2_port: isl.aswitch.inPort]
                topology.links << [node1: ASWITCH_NAME, node1_port: isl.aswitch.outPort,
                                   node2: isl.dstSwitch.name, node2_port: isl.dstPort]
            } else {
                topology.links << [node1: isl.srcSwitch.name, node1_port: isl.srcPort,
                                   node2: isl.dstSwitch.name, node2_port: isl.dstPort]
            }
        }
        topologyDefinition.notConnectedIsls.each { isl ->
            topology.links << [node1: isl.srcSwitch.name, node1_port: isl.srcPort,
                               node2: ASWITCH_NAME, node2_port: isl.aswitch.inPort]
        }
        return topology
    }
}
