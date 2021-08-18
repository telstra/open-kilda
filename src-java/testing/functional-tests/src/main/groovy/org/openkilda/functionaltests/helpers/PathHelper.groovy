package org.openkilda.functionaltests.helpers

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowPathPayload.FlowProtectedPath
import org.openkilda.northbound.dto.v2.flows.FlowPathV2
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
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
    static final Integer NOT_PREFERABLE_COST = 99999999

    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    IslUtils islUtils
    @Autowired
    Database database

    /**
     * All ISLs of the given path will have their cost set to a very high value.
     */
    void makePathNotPreferable(List<PathNode> path) {
        def notPreferableIsls = getInvolvedIsls(path)
        log.debug "ISLs to avoid: $notPreferableIsls"
        northbound.updateLinkProps(notPreferableIsls.collectMany { isl ->
            [islUtils.toLinkProps(isl, ["cost": (NOT_PREFERABLE_COST * 3).toString()])]
        })
    }

    /**
     * All ISLs of the given path will have their cost set to a very high value.
     */
    void makePathNotPreferable(FlowPathPayload path) {
        makePathNotPreferable(convert(path))
    }

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
     * Converts FlowPathPayload.FlowProtectedPath path representation to a List<PathNode> representation
     */
    static List<PathNode> convert(FlowProtectedPath pathPayload, pathToConvert = "forwardPath") {
        def path = pathPayload."$pathToConvert"
        getPathNodes(path)
    }

    /**
     * Returns a List<PathNode> representation of a path
     */
    static List<PathNode> getPathNodes(path) {
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
            pathNodes = pathNodes.dropRight(1).tail() //remove first and last elements (not used in PathNode view)
        }
        pathNodes.each { it.seqId = seqId++ } //set valid seqId indexes
        return pathNodes
    }

    /**
     * Converts FlowPathPayload path representation to a List<FlowPathV2.PathNodeV2> representation
     */
    static List<FlowPathV2.PathNodeV2> convertToNodesV2(FlowPathPayload pathPayload, pathToConvert = "forwardPath") {
        def path = pathPayload."$pathToConvert"
        if (path.empty) {
            throw new IllegalArgumentException("Path cannot be empty. " +
                    "This should be impossible for valid FlowPathPayload")
        }
        List<FlowPathV2.PathNodeV2> pathNodes = []
        path.each { pathEntry ->
            pathNodes << new FlowPathV2.PathNodeV2(pathEntry.switchId,
                    pathEntry.inputPort == null ? 0 : pathEntry.inputPort, null)
            pathNodes << new FlowPathV2.PathNodeV2(pathEntry.switchId,
                    pathEntry.outputPort == null ? 0 : pathEntry.outputPort, null)
        }
        if (pathNodes.size() > 2) {
            pathNodes = pathNodes.dropRight(1).tail() //remove first and last elements (not used in PathNode view)
        }
        return pathNodes
    }

    /**
     * Converts path nodes (in the form of List<PathNode>) to a List<FlowPathV2.PathNodeV2> representation
     */
    static List<FlowPathV2.PathNodeV2> convertToNodesV2(List<PathNode> path) {
        if (path.empty) {
            throw new IllegalArgumentException("Path cannot be empty.")
        }
        List<FlowPathV2.PathNodeV2> pathNodes = []
        path.each { pathEntry ->
            pathNodes << new FlowPathV2.PathNodeV2(pathEntry.switchId, pathEntry.portNo, pathEntry.segmentLatency)
        }
        return pathNodes
    }

    /**
     * Get list of switches involved in a given path.
     */
    List<Switch> getInvolvedSwitches(List<PathNode> path) {
        return (List<Switch>) getInvolvedIsls(path).collect { [it.srcSwitch, it.dstSwitch] }.flatten().unique()
    }

    /**
     * Get list of switches involved in an existing flow.
     */
    List<Switch> getInvolvedSwitches(String flowId) {
        return getInvolvedSwitches(convert(northbound.getFlowPath(flowId)))
    }

    /**
     * Get list of switches involved in an existing flow for protected path.
     */
    List<Switch> getInvolvedSwitchesForProtectedPath(String flowId) {
        return getInvolvedSwitches(convert(northbound.getFlowPath(flowId).protectedPath))
    }

    /**
     * Get total cost of all ISLs that are involved in a given path.
     *
     * @param path Path in List<PathNode> representation
     * @return ISLs cost
     */
    int getCost(List<PathNode> path) {
        return getInvolvedIsls(path).sum { database.getIslCost(it) } as int
    }
}
