package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.northbound.dto.v2.flows.FlowPathV2.PathNodeV2
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import java.util.AbstractMap.SimpleEntry
import java.util.stream.Collectors

/**
 * Holds utility methods for working with flow paths.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class PathHelper {

    @Autowired
    TopologyDefinition topology
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    IslUtils islUtils
    @Autowired
    Database database
    @Autowired
    CleanupManager cleanupManager


    /**
     * If required, makes one path more preferable than another.
     * Finds a unique ISL of less preferable path and adds 'cost difference between paths + 1' to its cost
     *
     * @param morePreferablePath path that should become more preferable over the 'lessPreferablePath'
     * @param lessPreferablePath path that should become less preferable compared to 'morePreferablePath'
     * @return The changed ISL (one-way ISL, but actually changed in both directions) or null
     */
    Isl makePathMorePreferable(List<PathNode> morePreferablePath, List<PathNode> lessPreferablePath) {
        def morePreferableIsls = getInvolvedIsls(morePreferablePath)
        def lessPreferableIsls = getInvolvedIsls(lessPreferablePath)
        List<Isl> uniqueIsls = (morePreferableIsls + lessPreferableIsls)
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        HashMap<Isl, Integer> islCosts = uniqueIsls.parallelStream().flatMap({ isl ->
            Integer cost = northbound.getLink(isl).cost ?: 700
            [isl, isl.reversed].stream().map({ biIsl -> new SimpleEntry<>(biIsl, cost) })
        }).collect(Collectors.toMap({ it.getKey() }, { it.getValue() }))
        // under specific condition cost of isl can be 0, but at the same time for the system 0 == 700
        def totalCostOfMorePrefPath = morePreferableIsls.sum { islCosts.get(it) }
        def totalCostOfLessPrefPath = lessPreferableIsls.sum { islCosts.get(it) }
        def difference = totalCostOfMorePrefPath - totalCostOfLessPrefPath
        def islToAvoid
        if (difference >= 0) {
            islToAvoid = lessPreferableIsls.find {
                !morePreferableIsls.contains(it) && !morePreferableIsls.contains(it.reversed)
            }
            if (!islToAvoid) {
                //this should be impossible
                throw new Exception("Unable to make some path more preferable because both paths use same ISLs")
            }
            log.debug "ISL to avoid: $islToAvoid"
            cleanupManager.addAction(DELETE_ISLS_PROPERTIES, {northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
            northbound.updateLinkProps([islUtils.toLinkProps(islToAvoid,
                    ["cost": (islCosts.get(islToAvoid) + difference + 1).toString()])])
        }
        return islToAvoid
    }


    /**
     * Get list of ISLs that are involved in given path.
     * Note: will only return forward-way isls. You'll have to reverse them yourself if required.
     * Note2: will try to search for an ISL in given topology.yaml. If not found, will create a new ISL object
     * with 0 bandwidth and null a-switch (which may not be the actual value)
     * Note3: poorly handle situation if switchId is not present in toppology.yaml at all (will create
     * ISL with src/dst switches as null)
     */
    List<Isl> getInvolvedIsls(List<PathNode> path) {
        if (path.size() % 2 != 0) {
            throw new IllegalArgumentException("Path should have even amount of nodes")
        }
        if (path.empty) {
            return new ArrayList<Isl>()
        }
        def involvedIsls = []
        for (int i = 1; i < path.size(); i += 2) {
            def src = path[i - 1]
            def dst = path[i]
            def matchingIsl = {
                it.srcSwitch?.dpId == src.switchId && it?.srcPort == src.portNo &&
                        it.dstPort == dst.portNo && it.dstSwitch.dpId == dst.switchId
            }
            def involvedIsl = topology.isls.find(matchingIsl) ?:
                    topology.isls.collect { it.reversed }.find(matchingIsl) ?:
                            Isl.factory(topology.switches.find { it.dpId == src.switchId }, src.portNo,
                                    topology.switches.find { it.dpId == dst.switchId }, dst.portNo,
                                    0, null)
            involvedIsls << involvedIsl
        }
        return involvedIsls
    }

    List<Isl> getInvolvedIsls(FlowPathPayload path) {
        getInvolvedIsls(convert(path))
    }

    List<Isl> getInvolvedIsls(String flowId) {
        getInvolvedIsls(convert(northbound.getFlowPath(flowId)))
    }

    /**
     * Converts FlowPathPayload path representation to a List<PathNode> representation
     */
    static List<PathNode> convert(FlowPathPayload pathPayload, pathToConvert = "forwardPath") {
        def path = pathPayload."$pathToConvert"
        getPathNodes(path)
    }



    /**
     * Returns a List<PathNode> representation of a path
     */
    static List<PathNode> getPathNodes(path, boolean removeTail = true) {
        if (path.empty) {
            throw new IllegalArgumentException("Path cannot be empty. " +
                    "This should be impossible for valid FlowPathPayload")
        }
        List<PathNode> pathNodes = []
        path.each { pathEntry ->
            pathNodes << new PathNode(pathEntry.switchId, pathEntry.inputPort == null ? 0 : pathEntry.inputPort, 0)
            pathNodes << new PathNode(pathEntry.switchId, pathEntry.outputPort == null ? 0 : pathEntry.outputPort, 0)
        }
        def seqId = 0
        if (pathNodes.size() > 2) {
            pathNodes = pathNodes.tail() //remove first elements (not used in PathNode view)
            if (removeTail) {
                pathNodes = pathNodes.dropRight(1) //remove last elements (not used in PathNode view)
            }
        }
        pathNodes.each { it.seqId = seqId++ } //set valid seqId indexes
        return pathNodes
    }


    /**
     * Converts List<PathNodeV2> path representation to the list of PathNodeV2, but with null segmentLatency field
     */
    static List<PathNodeV2> setNullLatency(List<PathNodeV2> path) {
        return path.collect { new PathNodeV2(it.switchId, it.portNo, null)}
    }

    /**
     * Get list of switches involved in a given path.
     */
    List<Switch> getInvolvedSwitches(List<PathNode> path) {
        return (List<Switch>) getInvolvedIsls(path).collect { [it.srcSwitch, it.dstSwitch] }.flatten().unique()
    }

}
