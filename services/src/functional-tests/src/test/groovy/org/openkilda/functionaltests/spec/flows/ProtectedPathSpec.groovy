package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""Protected path - it is pre-calculated, reserved, and deployed (except ingress rule),
so we can switch traffic fast.

- flow object is extended with a boolean parameter 'allocate_protected_path' with values false(default)
- /flows/{flow-id}/path returns 'main_path' + 'protected_path'.

System can start to use protected path in two case:
- main path is down;
- we send the 'swap' request for a flow with protected path('/v1/flows/{flow_id}/swap')

Main and protected paths can't use the same link.""")
class ProtectedPathSpec extends BaseSpecification {
    @Unroll
    def "Able to create a flow with protected path when maximumBandwidth=#bandwidth, vlan=#vlanId"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flow.source.vlanId = vlanId
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.id)
        flowPathInfo.protectedPath

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.id) }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.id).each { direction ->
            assert direction.discrepancies.empty
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        bandwidth | vlanId
        1000      | 3378
        0         | 3378
        1000      | 0
        0         | 0
    }

    def "Able to enable/disable protected path on a flow"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def currentLastUpdate = northbound.getFlow(flow.id).lastUpdated
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        def flowPathInfoAfterUpdating = northbound.getFlowPath(flow.id)
        flowPathInfoAfterUpdating.protectedPath

        currentLastUpdate < northbound.getFlow(flow.id).lastUpdated

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.id) }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def protectedFlowPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.id).protectedPath

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            protectedFlowPath.each { sw ->
                def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }

                assert flowHelper.findForwardPathRules(rules, sw).size() == 0
                assert flowHelper.findReversePathRules(rules, sw).size() == 0
            }
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to create a single switch flow with protected path"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Create single switch flow"
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Couldn't setup protected path for one-switch flow"
    }

    def "Unable to update a single switch flow to enable protected path"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        and: "A flow without protected path"
        def flow = flowHelper.singleSwitchFlow(sw)
        flowHelper.addFlow(flow)

        when: "Update flow: enable protected path"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Couldn't setup protected path for one-switch flow"

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
    }

    @Unroll
    def "System is able to switch #flowDescription flow to protected path"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def allIsls = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.id)
        flowPathInfo.protectedPath

        and: "Current path is not equal to protected path"
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        currentPath != currentProtectedPath

        and: "Bandwidth is reserved for protected path on involved ISLs"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { isl ->
            protectedIslsInfo.each { protectedIsl ->
                if (isl.id == protectedIsl.id) {
                    assert isl.availableBandwidth - protectedIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = pathHelper.getInvolvedIsls(currentPath)[0]
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flow is switched to protected path"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            def flowPathInfoAfterRerouting = northbound.getFlowPath(flow.id)

            assert pathHelper.convert(flowPathInfoAfterRerouting) != currentPath
            assert pathHelper.convert(flowPathInfoAfterRerouting) == currentProtectedPath
            assert pathHelper.convert(flowPathInfoAfterRerouting.protectedPath) != currentPath
            assert pathHelper.convert(flowPathInfoAfterRerouting.protectedPath) != currentProtectedPath
        }

        when: "Restore port status"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        then: "Path of the flow is not changed"
        pathHelper.convert(northbound.getFlowPath(flow.id)) == currentProtectedPath

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        database.resetCosts()

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Unroll
    def "System reroutes #flowDescription flow to more preferable path and ignore protected path when reroute is intentional"() {
        given: "Two active neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNeighboringSwitchPair(2)
        def allPaths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path

        and: "A flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.id)
        assert flowPathInfo.protectedPath

        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Init intentional reroute"
        def rerouteResponse = northbound.rerouteFlow(flow.id)

        then: "Flow is rerouted"
        rerouteResponse.rerouted

        and: "Path is not changed to protected path"
        def flowPathInfoAfterRerouting = northbound.getFlowPath(flow.id)
        def newCurrentPath = pathHelper.convert(flowPathInfoAfterRerouting)
        newCurrentPath != currentPath
        newCurrentPath != currentProtectedPath
        def newCurrentProtectedPath = pathHelper.convert(flowPathInfoAfterRerouting.protectedPath)
        newCurrentProtectedPath != currentPath
        newCurrentProtectedPath != currentProtectedPath
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteLinkProps(northbound.getAllLinkProps())

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Unroll
    def "System is able to switch #flowDescription flow to protected path and ignore more preferable path when reroute is automatical"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)
        def allPaths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path

        and: "A flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.id)
        assert flowPathInfo.protectedPath

        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = pathHelper.getInvolvedIsls(currentPath)[0]
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flow is switched to protected path"
        Wrappers.wait(WAIT_OFFSET) {
            def newPathInfo = northbound.getFlowPath(flow.id)
            def newCurrentPath = pathHelper.convert(newPathInfo)
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert newCurrentPath != currentPath

            def newCurrentProtectedPath = pathHelper.convert(newPathInfo.protectedPath)
            assert newCurrentPath == currentProtectedPath
            assert newCurrentProtectedPath != currentPath
            assert newCurrentProtectedPath != currentProtectedPath
        }

        when: "Restore port status"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        then: "Path of the flow is not changed"
        pathHelper.convert(northbound.getFlowPath(flow.id)) == currentProtectedPath

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Unroll
    def "A flow with protected path does not get rerouted if already on the best path"() {
        given: "Two active neighboring switches and a flow"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.id)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert flowPathInfo.protectedPath

        when: "Init intentional reroute"
        def rerouteResponse = northbound.rerouteFlow(flow.id)

        then: "Flow is not rerouted"
        !rerouteResponse.rerouted

        def newFlowPathInfo = northbound.getFlowPath(flow.id)
        pathHelper.convert(newFlowPathInfo) == currentPath
        pathHelper.convert(newFlowPathInfo.protectedPath) == currentProtectedPath

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to create a flow with protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found. " +
                "Couldn't find non overlapping protected path"

        and: "Cleanup: restore available bandwidth"
        isls.each { database.resetIslBandwidth(it) }
    }

    def "Unable to update a flow to enable protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Update flow: enable protected path"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Not enough bandwidth found or path not found. " +
                "Couldn't find non overlapping protected path"

        and: "Cleanup: delete the flow and restore available bandwidth"
        flowHelper.deleteFlow(flow.id)
        isls.each { database.resetIslBandwidth(it) }
    }

    def "Able to create a flow with protected path when there is not enough bandwidth and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flow.ignoreBandwidth = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath

        and: "Cleanup: delete the flow and restore available bandwidth"
        flowHelper.deleteFlow(flow.id)
        isls.each { database.resetIslBandwidth(it) }
    }

    def "System is able to recalculate protected path when protected path is broken"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def allIsls = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create a flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.id)
        flowPathInfo.protectedPath

        and: "Current path is not equal to protected path"
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        currentPath != currentProtectedPath

        and: "Bandwidth is reserved for protected path on involved ISLs"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { isl ->
            protectedIslsInfo.each { protectedIsl ->
                if (isl.id == protectedIsl.id) {
                    assert isl.availableBandwidth - protectedIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Break ISL on the protected path (bring port down) to init the recalculate procedure"
        def islToBreakProtectedPath = protectedIsls[0]
        northbound.portDown(islToBreakProtectedPath.dstSwitch.dpId, islToBreakProtectedPath.dstPort)

        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreakProtectedPath).get().state == IslChangeType.FAILED
        }

        then: "Protected path is recalculated"
        def newProtectedPath
        Wrappers.wait(WAIT_OFFSET) {
            newProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
            assert newProtectedPath != currentProtectedPath
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Current path is not changed"
        currentPath == pathHelper.convert(northbound.getFlowPath(flow.id))

        and: "Bandwidth is reserved for new protected path on involved ISLs"
        def newProtectedIsls = pathHelper.getInvolvedIsls(newProtectedPath)
        def allLinks = northbound.getAllLinks()
        def newProtectedIslsInfo = newProtectedIsls.collect { islUtils.getIslInfo(allLinks, it).get() }

        allIsls.each { isl ->
            newProtectedIslsInfo.each { protectedIsl ->
                if (isl.id == protectedIsl.id) {
                    assert isl.availableBandwidth - protectedIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        and: "Reservation is deleted on the broken ISL"
        def originInfoBrokenIsl = allIsls.find {
            it.destination.switchId == islToBreakProtectedPath.dstSwitch.dpId &&
                    it.destination.portNo == islToBreakProtectedPath.dstPort
        }
        def currentInfoBrokenIsl = islUtils.getIslInfo(allLinks, islToBreakProtectedPath).get()

        originInfoBrokenIsl.availableBandwidth == currentInfoBrokenIsl.availableBandwidth

        when: "Restore port status"
        northbound.portUp(islToBreakProtectedPath.dstSwitch.dpId, islToBreakProtectedPath.dstPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreakProtectedPath).get().state == IslChangeType.DISCOVERED
        }

        then: "Path is not recalculated again"
        pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath) == newProtectedPath

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        database.resetCosts()
    }

    @Unroll
    def "Unable to create #flowDescription flow with protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNeighboringSwitchPair(2)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        List<List<PathNode>> allPaths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path

        allPaths.sort { it.size() }[1..-1].unique {
            it.first()
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

        when: "Try to create a new flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found." +
                " Couldn't find non overlapping protected path"

        and: "Restore topology, delete flows and reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Unroll
    def "Unable to update #flowDescription flow to enable protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNeighboringSwitchPair(2)

        and: "A flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flowHelper.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path.findAll {
            it != pathHelper.convert(northbound.getFlowPath(flow.id))
        }.unique {
            it.first()
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

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Not enough bandwidth found or path not found." +
                " Couldn't find non overlapping protected path"

        and: "Restore topology, delete flows and reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    def "Able to swap main and protected paths manually"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        and: "Create a flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)
        def flowPathInfo = northbound.getFlowPath(flow.id)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        currentPath != currentProtectedPath

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.id) }

        when: "Swap flow paths"
        def currentLastUpdate = northbound.getFlow(flow.id).lastUpdated
        northbound.swapFlowPath(flow.id)

        then: "Flow paths are swapped"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPathInfoAfterSwapping = northbound.getFlowPath(flow.id)
        def newCurrentPath = pathHelper.convert(flowPathInfoAfterSwapping)
        def newCurrentProtectedPath = pathHelper.convert(flowPathInfoAfterSwapping.protectedPath)
        newCurrentPath != currentPath
        newCurrentPath == currentProtectedPath
        newCurrentProtectedPath != currentProtectedPath
        newCurrentProtectedPath == currentPath

        currentLastUpdate < northbound.getFlow(flow.id).lastUpdated

        and: "All rules for main and protected paths are updated"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.id) }

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to swap paths when allocate_protected_path=false"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "A flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Try to swap paths for flow that doesn't have protected path"
        northbound.swapFlowPath(flow.id)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not swap paths: Flow $flow.id doesn't have protected path"

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to swap paths for a non-existent flow"() {
        when: "Try to swap path on a non-existent flow"
        northbound.swapFlowPath(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not swap paths: Flow $NON_EXISTENT_FLOW_ID not found"
    }

    def "Unable to swap paths for an inactive flow"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNeighboringSwitchPair(2)

        and: "A flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path.findAll {
            it != pathHelper.convert(northbound.getFlowPath(flow.id)) &&
                    it != pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        }.unique {
            it.first()
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

        when: "Break ISL on the protected/main paths (bring port down) for changing the flow state to down"
        def flowPathInfo = northbound.getFlowPath(flow.id)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        northbound.portDown(protectedIsls[0].dstSwitch.dpId, protectedIsls[0].dstPort)
        northbound.portDown(currentIsls[0].dstSwitch.dpId, currentIsls[0].dstPort)

        then: "Flow state is changed to down"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Try to swap the inactive flow"
        northbound.swapFlowPath(flow.id)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not swap paths: Protected flow path $flow.id is not in ACTIVE state"

        when: "Restore ISL for the main path only"
        northbound.portUp(currentIsls[0].srcSwitch.dpId, currentIsls[0].srcPort)
        northbound.portUp(currentIsls[0].dstSwitch.dpId, currentIsls[0].dstPort)

        then: "Flow state is still DOWN"
        Wrappers.timedLoop(WAIT_OFFSET){ assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Restore ISL for the protected path"
        northbound.portUp(protectedIsls[0].srcSwitch.dpId, protectedIsls[0].srcPort)
        northbound.portUp(protectedIsls[0].dstSwitch.dpId, protectedIsls[0].dstPort)

        then: "Flow state is changed to UP"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Cleanup: Restore topology, delete flows and reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Unroll
    def "#flowDescription flow is DOWN when protected and alternative paths are not available"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNeighboringSwitchPair(2)

        and: "A flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = (bandwidth == 0) ? true : false
        flowHelper.addFlow(flow)
        def flowInfoPath = northbound.getFlowPath(flow.id)
        assert flowInfoPath.protectedPath

        when: "All alternative paths are unavailable (bring ports down on the source switch and on the protected path)"
        List<PathNode> broughtDownPorts = []
        database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path.findAll {
            it != pathHelper.convert(northbound.getFlowPath(flow.id))
        }.unique {
            it.first()
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

        then: "Flow status is DOWN"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })

        then: "Flow status is UP"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Cleanup: Restore topology, delete flow and reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()

        where:
        flowDescription | bandwidth
        "A metered"     | 1000
        "An unmetered"  | 0
    }

    List<Switch> getNotNeighboringSwitchPair(minPossiblePath) {
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            /**just for sure that we have non overlapping path available
             * and we make assumption that parallel links won't be used by a flow
             * because another switch is available */
            List<List<Switch>> pathSwitches = possibleFlowPaths.collect { pathHelper.getInvolvedSwitches(it) }
            Integer numberOfDifferentSwitchInPath = 0
            for (int i = 0; i < pathSwitches.size() - 1; i++) {
                if (!pathSwitches[i][1..-2].containsAll(pathSwitches[i + 1][1..-2])) {
                    numberOfDifferentSwitchInPath += 1
                }
            }
            allLinks.every { link ->
                !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId)
            } && possibleFlowPaths.size() >= minPossiblePath && numberOfDifferentSwitchInPath >= 2
        } ?: assumeTrue("No suiting switches found", false)

        return [srcSwitch, dstSwitch]
    }

    List<Switch> getNeighboringSwitchPair(minPossiblePath) {
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= minPossiblePath
        } ?: assumeTrue("No suiting switches found", false)

        return [srcSwitch, dstSwitch]
    }
}
