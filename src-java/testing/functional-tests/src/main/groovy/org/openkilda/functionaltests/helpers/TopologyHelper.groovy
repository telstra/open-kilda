package org.openkilda.functionaltests.helpers


import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Status
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGenConfig
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.northbound.NorthboundService

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Memoized
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
    @Autowired
    PathHelper pathHelper

    List<SwitchTriplet> getSwitchTriplets(boolean includeReverse = false, boolean includeSingleSwitch = false) {
        //get deep copy
        def mapper = new ObjectMapper()
        def result = mapper.readValue(mapper.writeValueAsString(getSwitchTripletsCached(includeSingleSwitch)), SwitchTriplet[]).toList()
        return includeReverse ? result.collectMany { [it, it.reversed] } : result
    }

    SwitchTriplet getSwitchTriplet(SwitchId shared, SwitchId ep1, SwitchId ep2) {
        return getSwitchTriplets(true, true).find {
            it.shared.getDpId() == shared
        && it.ep1.getDpId() == ep1
        && it.ep2.getDpId() == ep2
        }
    }

    List<SwitchTriplet> getAllNotNeighbouringSwitchTriplets(boolean includeReverse = false) {
        return getSwitchTriplets(includeReverse).findAll {
            it.shared != it.ep1 && it.ep1 != it.ep2 && it.ep2 != it.shared &&
            it.getPathsEp1().every {it.size() > 2} &&
                    it.getPathsEp2().every {it.size() > 2}}
    }

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

    Switch getRandomSwitch() {
        return topology.getActiveSwitches().shuffled().first()
    }

    SwitchTriplet findSwitchTripletWithDifferentEndpoints() {
        return switchTriplets.find { SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it) }
    }

    SwitchTriplet findSwitchTripletWithAlternativeFirstPortPaths() {
        return switchTriplets.find {
            if (!SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it)) {
                return false
            }
            def firstIslPorts = it.pathsEp1*.first().portNo as Set
            firstIslPorts.addAll(it.pathsEp2*.first().portNo)
            return firstIslPorts.size() > 1
        }
    }

    SwitchTriplet findSwitchTripletWithAlternativePaths() {
        return switchTriplets.find {
            if (!SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it)) {
                return false
            }
            return it.pathsEp1.size() > 1 || it.pathsEp2.size() > 1
        }
    }

    SwitchTriplet findSwitchTripletWithYPointOnSharedEp() {
        return getSwitchTriplets(true, false).find {
            findPotentialYPoints(it).size() == 1 && it.getShared().getDpId() == findPotentialYPoints(it).get(0).getDpId()
        }
    }

    SwitchTriplet findSwitchTripletWithSharedEpInTheMiddleOfTheChainServer42Support() {
        def server42switches = topology.getActiveServer42Switches()
        return switchTriplets.findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT).find {
            it.shared.dpId in server42switches.dpId
                && it.ep1.dpId in server42switches.dpId
                && it.ep2.dpId in server42switches.dpId
                && (it.pathsEp1[0].size() == 2 && it.pathsEp2[0].size() == 2) }
    }

    SwitchTriplet findSwitchTripletWithSharedEpThatIsNotNeighbourToEp1AndEp2Server42Support() {
        def server42switches = topology.getActiveServer42Switches()
        return switchTriplets.findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT).find {
            it.shared.dpId in server42switches.dpId
                    && it.ep1.dpId in server42switches.dpId
                    && it.ep2.dpId in server42switches.dpId
                    && (it.pathsEp1[0].size() > 2 && it.pathsEp2[0].size() > 2) }
    }

    SwitchTriplet findSwitchTripletServer42SupportWithSharedEpInTheMiddleOfTheChainExceptWBSw() {
        def server42switches = topology.getActiveServer42Switches()
        return switchTriplets.findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT).findAll(SwitchTriplet.NOT_WB_ENDPOINTS).find {
            it.shared.dpId in server42switches.dpId
                    && it.ep1.dpId in server42switches.dpId
                    && it.ep2.dpId in server42switches.dpId
                    && (it.pathsEp1[0].size() == 2 && it.pathsEp2[0].size() == 2) }
    }

    SwitchTriplet findSwitchTripletWithOnlySharedSwServer42Support() {
        def server42switches = topology.getActiveServer42Switches()
        return switchTriplets.findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT).find {
            it.shared.dpId in server42switches.dpId
                    && !(it.ep1.dpId in server42switches.dpId)
                    && !(it.ep2.dpId in server42switches.dpId)
        }
    }

    SwitchTriplet findSwitchTripletWithSharedEpEp1Ep2InChainServer42Support() {
        def server42switches = topology.getActiveServer42Switches()
        return switchTriplets.findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT).find {
            def areEp1Ep2AndEp1OrEp2AndShEpNeighbour
            if(it.pathsEp1[0].size() == 2 && it.pathsEp2[0].size() > 2) {
                //both pair sh_ep+ep1 and ep1+ep2 are neighbours, sh_ep and ep2 is not neighbour
                areEp1Ep2AndEp1OrEp2AndShEpNeighbour = it.pathsEp2.find {ep2Path -> ep2Path.containsAll(it.pathsEp1[0]) && ep2Path.size() == 4 }
            } else if(it.pathsEp2[0].size() == 2 && it.pathsEp1[0].size() > 2) {
                //both pair sh_ep+ep2 and ep2+ep1 are neighbours, sh_ep and ep1 is not neighbour
                areEp1Ep2AndEp1OrEp2AndShEpNeighbour = it.pathsEp1.find {ep1Path -> ep1Path.containsAll(it.pathsEp2[0]) && ep1Path.size() == 4}
            }
            it.shared.dpId in server42switches.dpId
                    && it.ep1.dpId in server42switches.dpId
                    && it.ep2.dpId in server42switches.dpId
                    && areEp1Ep2AndEp1OrEp2AndShEpNeighbour}
    }

    SwitchTriplet findSwitchTripletForHaFlowWithProtectedPaths() {
        return switchTriplets.find {
            if (!SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it)) {
                return false
            }
            def pathsCombinations = [it.pathsEp1, it.pathsEp2].combinations()
            for (i in 0..<pathsCombinations.size()) {
                for (j in i + 1..<pathsCombinations.size()) {
                    if (!areHaPathsIntersect(pathsCombinations[i], pathsCombinations[j])) {
                        return true
                    }
                }
            }
            return false
        }
    }

    SwitchTriplet findSwitchTripletWithSharedEpEp1Ep2InChain() {
        return switchTriplets.findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT).find {
            def areEp1Ep2AndEp1OrEp2AndShEpNeighbour = null
            if(it.pathsEp1[0].size() == 2 && it.pathsEp2[0].size() > 2) {
                //both pair sh_ep+ep1 and ep1+ep2 are neighbours, sh_ep and ep2 is not neighbour
                areEp1Ep2AndEp1OrEp2AndShEpNeighbour = it.pathsEp2.find {ep2Path -> ep2Path.containsAll(it.pathsEp1[0]) && ep2Path.size() == 4 }
            } else if(it.pathsEp2[0].size() == 2 && it.pathsEp1[0].size() > 2) {
                //both pair sh_ep+ep2 and ep2+ep1 are neighbours, sh_ep and ep1 is not neighbour
                areEp1Ep2AndEp1OrEp2AndShEpNeighbour = it.pathsEp1.find {ep1Path -> ep1Path.containsAll(it.pathsEp2[0]) && ep1Path.size() == 4}
            }
            areEp1Ep2AndEp1OrEp2AndShEpNeighbour}
    }

    Switch getSwitch(SwitchId id) {
        return topology.getSwitches().find{it.getDpId() == id}
    }

    int getTraffgenPortBySwitchId(SwitchId id) {
        return topology.getSwitches().find{it.getDpId() == id}.getTraffGens().first().getSwitchPort()
    }

    private static boolean areHaPathsIntersect(subPaths1, subPaths2) {
        for (List<PathNode> subPath1 : subPaths1) {
            for (List<PathNode> subPath2 : subPaths2) {
                if (subPaths1.intersect(subPaths2)) {
                    return true
                }
            }
        }
        return false
    }

    private static Status switchStateToStatus(SwitchChangeType state) {
        switch (state) {
            case SwitchChangeType.ACTIVATED:
                return Status.Active
            default:
                return Status.Inactive
        }
    }

    @Memoized
    List<Switch> findPotentialYPoints(SwitchTriplet swT) {
        def sortedEp1Paths = swT.pathsEp1.sort { it.size() }
        def potentialEp1Paths = sortedEp1Paths.takeWhile { it.size() == sortedEp1Paths[0].size() }
        def potentialEp2Paths = potentialEp1Paths.collect { potentialEp1Path ->
            def sortedEp2Paths = swT.pathsEp2.sort {
                it.size() - it.intersect(potentialEp1Path).size()
            }
            [path1: potentialEp1Path,
             potentialPaths2: sortedEp2Paths.takeWhile {it.size() == sortedEp2Paths[0].size() }]
        }
        return potentialEp2Paths.collectMany {path1WithPath2 ->
            path1WithPath2.potentialPaths2.collect { List<PathNode> potentialPath2 ->
                def switches = pathHelper.getInvolvedSwitches(path1WithPath2.path1)
                        .intersect(pathHelper.getInvolvedSwitches(potentialPath2))
                switches ? switches[-1] : null
            }
        }.findAll().unique()
    }



    @Memoized
    private List<SwitchTriplet> getSwitchTripletsCached(boolean includeSingleSwitch) {
        return [topology.activeSwitches, topology.activeSwitches, topology.activeSwitches].combinations()
                .findAll { shared, ep1, ep2 -> includeSingleSwitch || shared != ep1 && shared != ep2 } //non-single-switch
                .unique { triplet -> [triplet[0], [triplet[1], triplet[2]].sort()] } //no reversed versions of ep1/ep2
                .collect { Switch shared, Switch ep1, Switch ep2 ->
                    new SwitchTriplet(shared, ep1, ep2, getDbPathsCached(shared.dpId, ep1.dpId), getDbPathsCached(shared.dpId, ep2.dpId))
                }
    }

    @Memoized
    List<List<PathNode>> getDbPathsCached(SwitchId src, SwitchId dst) {
        database.getPaths(src, dst)*.path
    }
}
