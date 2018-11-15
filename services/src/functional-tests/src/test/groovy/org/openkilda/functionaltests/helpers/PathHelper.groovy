package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.northbound.dto.links.LinkPropsDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Holds utility methods for working with flow paths.
 */
@Component
@Slf4j
class PathHelper {
    static final String UNPREFERABLE_COST = "99999999"

    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northbound
    @Autowired
    IslUtils islUtils

    /**
     * Selects ISL that is present only in less preferable path and is not present in more preferable one. Then
     * sets very big cost on that ISL, so that the path indeed becomes less preferable.
     *
     * @param morePreferablePath path that should become more preferable over the 'lessPreferablePath'
     * @param lessPreferablePath path that should become less preferable compared to 'morePreferablePath'
     */
    void makePathMorePreferable(List<PathNode> morePreferablePath, List<PathNode> lessPreferablePath) {
        def morePreferableIsls = getInvolvedIsls(morePreferablePath)
        def islToAvoid = getInvolvedIsls(lessPreferablePath).find { !morePreferableIsls.contains(it) }
        log.debug "ISL to avoid: $islToAvoid"
        if (!islToAvoid) {
            throw new Exception("Unable to make some path more preferable because both paths use same ISLs")
        }
        northbound.updateLinkProps([
                new LinkPropsDto(islToAvoid.srcSwitch.dpId.toString(), islToAvoid.srcPort,
                        islToAvoid.dstSwitch.dpId.toString(), islToAvoid.dstPort, ["cost": UNPREFERABLE_COST]),
                new LinkPropsDto(islToAvoid.dstSwitch.dpId.toString(), islToAvoid.dstPort,
                        islToAvoid.srcSwitch.dpId.toString(), islToAvoid.srcPort, ["cost": UNPREFERABLE_COST])])
    }

    /**
     * Get list of ISLs that are involved in given path.
     * Note: will only return forward-way isls. You'll have to reverse them yourself if required via IslUtils.
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
                    topology.isls.collect { islUtils.reverseIsl(it) }.find(matchingIsl) ?:
                            Isl.factory(topology.switches.find { it.dpId == src.switchId },
                                    src.portNo, topology.switches.find {
                                it.dpId == dst.switchId
                            }, dst.portNo, 0, null)
            involvedIsls << involvedIsl
        }
        return involvedIsls
    }

    /**
     * Converts FlowPathPayload path representation to a List<PathNode> representation
     */
    static List<PathNode> convert(FlowPathPayload pathPayload) {
        def path = pathPayload.forwardPath
        if (path.empty) {
            throw new IllegalArgumentException("Path cannot be empty. " +
                    "This should be impossible for valid FlowPathPayload")
        }
        List<PathNode> pathNodes = []
        path.each { pathEntry ->
            pathNodes << new PathNode(pathEntry.switchId, pathEntry.inputPort, 0)
            pathNodes << new PathNode(pathEntry.switchId, pathEntry.outputPort, 0)
        }
        def seqId = 0
        pathNodes = pathNodes.dropRight(1).tail() //remove first and last elements (not used in PathNode view)
        pathNodes.each { it.seqId = seqId++ } //set valid seqId indexes
        return pathNodes
    }

    /**
     * Get list of Switches involved in given path.
     */
    List<Switch> getInvolvedSwitches(List<PathNode> path) {
        return (List<Switch>)getInvolvedIsls(path).collect { [it.srcSwitch, it.dstSwitch] }.flatten().unique()
    }

    /**
     * Get list of Switches involved in given flow.
     */
    List<Switch> getInvolvedSwitches(String flowId) {
        def flowPath = northbound.getFlowPath(flowId)
        assert flowPath.forwardPath
        def switchIds = flowPath.forwardPath.unique()*.switchId
        return topology.switches.findAll {sw -> sw.dpId in switchIds}.unique()
    }

    /**
     * Get total cost of all ISLs that are involved in a given path.
     *
     * @param path Path in List<PathNode> representation
     * @return ISLs cost
     */
    int getCost(List<PathNode> path) {
        return getInvolvedIsls(path).sum { islUtils.getIslCost(it) } as int
    }
}
