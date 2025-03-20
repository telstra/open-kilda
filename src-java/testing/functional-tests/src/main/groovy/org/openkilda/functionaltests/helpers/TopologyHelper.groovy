package org.openkilda.functionaltests.helpers


import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Status
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGenConfig
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class TopologyHelper {
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("northboundServiceImpl")
    NorthboundService nb
    @Autowired
    TopologyDefinition topology
    @Autowired
    Database database
    @Autowired
    FloodlightsHelper flHelper

    TopologyDefinition readCurrentTopology() {
        readCurrentTopology(false)
    }

    TopologyDefinition readCurrentTopology(Boolean generateTopology) {
        def switches = generateTopology ? nb.getAllSwitches() : northbound.getAllSwitches()
        def links = generateTopology ? nb.getAllLinks() : northbound.getAllLinks()
        def i = 0
        def switchIdsPerRegion = flHelper.fls.collectEntries {
            [(it.region): it.floodlightService.getSwitches()*.switchId] }
        def topoSwitches = switches.collect { sw ->
            i++
            List<String> applicableRegions = switchIdsPerRegion.findAll { it.value.contains(sw.switchId) }*.key
            new Switch("ofsw$i", sw.switchId, sw.ofVersion, switchStateToStatus(sw.state), applicableRegions, [],
                    null, null, null)
        }
        def topoLinks = links.collect { link ->
            new Isl(topoSwitches.find { it.dpId == link.source.switchId }, link.source.portNo,
                    topoSwitches.find { it.dpId == link.destination.switchId }, link.destination.portNo,
                    link.maxBandwidth, null)
        }.unique { a, b -> a == b || a == b.reversed ? 0 : 1 }

        return new TopologyDefinition(topoSwitches, topoLinks, [], TraffGenConfig.defaultConfig())
    }

    private static Status switchStateToStatus(SwitchChangeType state) {
        switch (state) {
            case SwitchChangeType.ACTIVATED:
                return Status.Active
            default:
                return Status.Inactive
        }
    }

    static List<List<PathNodePayload>> convertToPathNodePayload(List<List<PathNode>> paths) {
        paths.parallelStream().collect { path ->
            def result = [new PathNodePayload(path[0].getSwitchId(), null, path[0].getPortNo())]
            for (int i = 1; i < path.size() - 1; i += 2) {
                result.add(new PathNodePayload(path.get(i).getSwitchId(),
                        path.get(i).getPortNo(),
                        path.get(i + 1).getPortNo()))
            }
            result.add(new PathNodePayload(path[-1].getSwitchId(), path[-1].getPortNo(), null))
            result
        }
    }
}
