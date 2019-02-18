package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.northbound.dto.links.LinkPropsDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Value

class FlowDiversitySpec extends BaseSpecification {

    @Value('${diversity.isl.weight}')
    int diversityIslWeight

    def "Able to create diverse flows"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3

        } ?: assumeTrue("No suiting switches found", false)

        when: "Create three flows with diversity enabled"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch, false)
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1]).tap { it.diverseFlowId = flow1.id }
        def flow3 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1, flow2]).tap {
            it.diverseFlowId = flow2.id
        }
        [flow1, flow2, flow3].each { flowHelper.addFlow(it) }

        then: "All flows have different paths"
        def allInvolvedIsls = [flow1, flow2, flow3].collect {
            pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(it.id)))
        }.flatten()
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Delete flows"
        [flow1, flow2, flow3].each { flowHelper.deleteFlow(it.id) }
    }

    def "Able to update flows to become diverse"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3

        } ?: assumeTrue("No suiting switches found", false)

        and: "Create three flows"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch, false)
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1])
        def flow3 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1, flow2])
        [flow1, flow2, flow3].each { flowHelper.addFlow(it) }

        def (flow1Path, flow2Path, flow3Path) = [flow1, flow2, flow3].collect {
            PathHelper.convert(northbound.getFlowPath(it.id))
        }
        assert [flow1Path, flow2Path, flow3Path].toSet().size() == 1

        when: "Update the second flow to become diverse"
        flowHelper.updateFlow(flow2.id, flow2.tap { it.diverseFlowId = flow1.id })

        then: "The flow became diverse and changed the path"
        def flow2PathUpdated = PathHelper.convert(northbound.getFlowPath(flow2.id))
        flow2PathUpdated != flow2Path

        when: "Update the third flow to become diverse"
        flowHelper.updateFlow(flow3.id, flow3.tap { it.diverseFlowId = flow2.id })

        then: "The flow became diverse and all flows have different paths"
        def flow3PathUpdated = PathHelper.convert(northbound.getFlowPath(flow3.id))
        [flow1Path, flow2PathUpdated, flow3PathUpdated].toSet().size() == 3

        def allInvolvedIsls = [flow1Path, flow2PathUpdated, flow3PathUpdated].collect {
            pathHelper.getInvolvedIsls(it)
        }.flatten()
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Delete flows"
        [flow1, flow2, flow3].each { flowHelper.deleteFlow(it.id) }
    }

    def "Able to update flows to become not diverse"() {
        given: "Two active neighboring switches with three not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3

        } ?: assumeTrue("No suiting switches found", false)

        and: "Create three flows with diversity enabled"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch, false)
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1]).tap { it.diverseFlowId = flow1.id }
        def flow3 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1, flow2]).tap {
            it.diverseFlowId = flow2.id
        }
        [flow1, flow2, flow3].each { flowHelper.addFlow(it) }

        def (flow1Path, flow2Path, flow3Path) = [flow1, flow2, flow3].collect {
            PathHelper.convert(northbound.getFlowPath(it.id))
        }
        def allInvolvedIsls = [flow1Path, flow2Path, flow3Path].collect { pathHelper.getInvolvedIsls(it) }.flatten()
        assert allInvolvedIsls.unique(false) == allInvolvedIsls

        when: "Update the second flow to become not diverse"
        flowHelper.updateFlow(flow2.id, flow2.tap { it.diverseFlowId = null })

        then: "The flow became not diverse and rerouted to the more preferable path (path of the first flow)"
        def flow2PathUpdated = PathHelper.convert(northbound.getFlowPath(flow2.id))
        flow2PathUpdated != flow2Path
        flow2PathUpdated == flow1Path

        when: "Update the third flow to become not diverse"
        flowHelper.updateFlow(flow3.id, flow3.tap { it.diverseFlowId = null })

        then: "The flow became not diverse and rerouted to the more preferable path (path of the first flow)"
        def flow3PathUpdated = PathHelper.convert(northbound.getFlowPath(flow3.id))
        flow3PathUpdated != flow3Path
        flow3PathUpdated == flow1Path

        and: "Delete flows"
        [flow1, flow2, flow3].each { flowHelper.deleteFlow(it.id) }
    }

    def "Diverse flows are built through the same path if there are no alternative paths available"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            allFlowPaths.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2

        } ?: assumeTrue("No suiting switches found", false)

        and: "Create a flow going through these switches"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow1)
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))

        and: "Make all alternative paths unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        allFlowPaths.findAll { it != flow1Path }.unique { it.first() }.findAll {
            it.first().portNo != flow1Path.first().portNo
        }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Create the second flow with diversity enabled"
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1]).tap { it.diverseFlowId = flow1.id }
        flowHelper.addFlow(flow2)
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        then: "The second flow is built through the same path as the first flow"
        flow2Path == flow1Path

        and: "Restore topology, delete flows and reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    def "Cost of link from a diversity group is increased when calculating diverse flow paths"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            allFlowPaths.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2

        } ?: assumeTrue("No suiting switches found", false)

        and: "Create a flow going through these switches"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow1)
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))

        and: "Make all alternative paths less preferable by 1 for diverse flows"
        allFlowPaths.findAll { it != flow1Path }.unique { it.first() }.findAll {
            it.first().portNo != flow1Path.first().portNo
        }.each {
            def isl = pathHelper.getInvolvedIsls(it)[0]
            northbound.updateLinkProps([new LinkPropsDto(isl.srcSwitch.dpId.toString(), isl.srcPort,
                    isl.dstSwitch.dpId.toString(), isl.dstPort, ["cost": (diversityIslWeight + 1).toString()])])
        }

        when: "Create the second flow with diversity enabled"
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1]).tap { it.diverseFlowId = flow1.id }
        flowHelper.addFlow(flow2)
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        then: "The flow is built through the most preferable path (path of the first flow)"
        flow2Path == flow1Path

        when: "Create the third flow with diversity enabled"
        def flow3 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1, flow2]).tap {
            it.diverseFlowId = flow2.id
        }
        flowHelper.addFlow(flow3)
        def flow3Path = PathHelper.convert(northbound.getFlowPath(flow3.id))

        then: "The flow is built through one of the alternative paths because they are preferable already"
        def involvedIsls = [flow2Path, flow3Path].collect { pathHelper.getInvolvedIsls(it) }.flatten()
        flow3Path != flow2Path
        involvedIsls.unique(false) == involvedIsls

        and: "Delete flows and link props"
        [flow1, flow2, flow3].each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
    }
}
