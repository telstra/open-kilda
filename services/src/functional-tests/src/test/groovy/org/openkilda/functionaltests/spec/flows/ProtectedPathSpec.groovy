package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.model.Cookie
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("TBD")
class ProtectedPathSpec extends BaseSpecification {
    @Unroll
    def "Able to create flow with the 'protected path' option in case maximumBandwidth=#bandwidth, vlan=#vlanId"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.source.vlanId = vlanId
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }
        northbound.getFlowPath(flow.id).protectedPath

        and: "Rules for protected path are created"
        def mainFlowPath = northbound.getFlowPath(flow.id).forwardPath
        def protectedFlowPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        def commonNodeIds = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)
        Wrappers.wait(WAIT_OFFSET) {
            verifyRulesOnCommonNodes(commonNodeIds, srcSwitch.dpId, dstSwitch.dpId, mainFlowPath, protectedFlowPath)
        }

        def uniqueNodes = protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        }
        verifyRulesOnUniqueSwitches(uniqueNodes)

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        bandwidth | vlanId
        1000      | 3378
        0         | 3378
        1000      | 0
        0         | 0
    }

    def "Able to update the 'protected path' option"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        and: "Needed number of rules are created"
        northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll { !Cookie.isDefaultRule(it.cookie) }.size() == 2

        pathHelper.getInvolvedSwitches(flow.id)[1..-1].each { sw ->
            assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }.size() == 2
        }

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def currentLastUpdate = northbound.getFlow(flow.id).lastUpdated
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        northbound.getFlowPath(flow.id).protectedPath

        def newCurrentLastUpdate = northbound.getFlow(flow.id).lastUpdated
        currentLastUpdate < newCurrentLastUpdate

        and: "Rules are updated on the main path"
        def mainFlowPath = northbound.getFlowPath(flow.id).forwardPath
        def protectedFlowPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        def commonNodeIds = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)
        Wrappers.wait(WAIT_OFFSET) {
            verifyRulesOnCommonNodes(commonNodeIds, srcSwitch.dpId, dstSwitch.dpId, mainFlowPath, protectedFlowPath)
        }

        def uniqueNodes = protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        }
        verifyRulesOnUniqueSwitches(uniqueNodes)

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.id).protectedPath

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            commonNodeIds.each { sw ->
                def rules = northbound.getSwitchRules(sw).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                if (sw == srcSwitch.dpId || sw == dstSwitch.dpId) {
                    assert rules.size() == 2

                    def mainNode = mainFlowPath.find { it.switchId == sw }
                    assert filterRules(rules, mainNode, true, false).size() == 1
                    assert filterRules(rules, mainNode, false, true).size() == 1
                }
            }
        }

        uniqueNodes.each { sw ->
            def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            def mainNode = mainFlowPath.find { it.switchId == sw.switchId }
            if (mainNode) {
                assert rules.size() == 2
                assert filterRules(rules, sw, true, false).size() == 1
                assert filterRules(rules, sw, false, true).size() == 1
            }

            def protectedNode = protectedFlowPath.find { it.switchId == sw.switchId }
            if (protectedNode) {
                assert rules.size() == 0
            }
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to create flow with the 'protected path' option on a single switch flow"() {
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

    def "Unable to update flow with the 'protected path' option on a single switch flow"() {
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
    def "System is able to reroute #flowDescription flow to protected path"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def allIsls = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Current path is not equal to protected path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        and: "Bandwidth is reserved for protected path on involved isls"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { originIsl ->
            protectedIslsInfo.each { newIsl ->
                if (originIsl.id == newIsl.id) {
                    assert originIsl.availableBandwidth - newIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Init reroute"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def islToBreak = currentIsls.find { !protectedIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flow is rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) != currentPath
        }

        and: "Current path is protected path"
        pathHelper.convert(northbound.getFlowPath(flow.id)) == currentProtectedPath
        pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath) == currentPath

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
        "metered"       | 1000
        "unmetered"     | 0
    }

    @Unroll
    def "System reroutes #flowDescription flow to more preferable path and ignore protected path in case reroute is intentional"() {
        given: "A flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 2
        } ?: assumeTrue("No suiting switches found", false)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        northbound.getFlowPath(flow.id).protectedPath
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Init reroute"
        def rerouteResponse = northbound.rerouteFlow(flow.id)

        then: "Flow is rerouted"
        rerouteResponse.rerouted

        and: "Path is not changed to protected path"
        def newCurrentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        newCurrentPath != currentPath
        newCurrentPath != currentProtectedPath
        def newCurrentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        newCurrentProtectedPath != currentPath
        newCurrentProtectedPath != currentProtectedPath

        then: "Flow is rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) != currentPath
        }

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        database.resetCosts()

        where:
        flowDescription | bandwidth
        "metered"       | 1000
        "unmetered"     | 0
    }

    @Unroll
    def "System reroutes #flowDescription flow to protected path  and ignore more preferable path in case reroute is automatical"() {
        given: "A flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 2
        } ?: assumeTrue("No suiting switches found", false)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        northbound.getFlowPath(flow.id).protectedPath
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Init reroute"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def islToBreak = currentIsls.find { !protectedIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flow is rerouted"
        def newCurrentPath
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            newCurrentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert newCurrentPath != currentPath
        }

        and: "Current path is protected path and new protected path is created"
        Wrappers.wait(WAIT_OFFSET) {
            def newCurrentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
            newCurrentPath == currentProtectedPath
            newCurrentProtectedPath != currentPath
            newCurrentProtectedPath != currentProtectedPath
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
    def "Able to get flow(protectedPath=#protectedPath) path in case flow is not rerouted"() {
        given: "Two active neighboring switches and a flow"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = protectedPath
        flowHelper.addFlow(flow)

        when: "Init reroute"
        def rerouteResponse = northbound.rerouteFlow(flow.id)

        then: "Flow is not rerouted"
        !rerouteResponse.rerouted

        and: "System allows to get flow path"
        northbound.getFlowPath(flow.id)

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)

        where:
        protectedPath << [false, true]
    }

    def "Unable to create a flow with the the 'protected path' option in case there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all isls which can be used by protected path"
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

    def "Unable to update the 'protected path' option on a flow in case there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all isls which can be used by protected path"
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

    def "Able to create a flow with 'protected path' in case there is not enough bandwidth and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all isls which can be used by protected path"
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

    @Ignore("Test is not valid, the availableBandwidth field is rewritten by each update action")
    def "Able to enable protected path on a flow in case there is not enough bandwidth for protectedPath and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all isls which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { isl ->
            database.updateIslAvailableBandwidth(isl, 90)
            database.updateIslAvailableBandwidth(isl.reversed, 90)
        }

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = false
        flow.ignoreBandwidth = true
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        northbound.getFlowPath(flow.id).protectedPath

        // TODO I've second thoughts about the following lines
        northbound.updateFlow(flow.id, flow.tap { it.ignoreBandwidth = false })
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })
        //Should the protected path be enabled?
//        isls[1..-1].each { isl ->
//            database.updateIslAvailableBandwidth(isl, 90)
//            database.updateIslAvailableBandwidth(isl.reversed, 90)
//        }
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        and: "Cleanup: delete the flow and restore available bandwidth"
        flowHelper.deleteFlow(flow.id)
        isls.each { isl ->
            database.resetIslBandwidth(isl)
            database.resetIslBandwidth(isl.reversed)
        }
    }

    def "System is able to recalculate protected path when port is down"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def allIsls = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        when: "Create a flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Current path is not equal to protected path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        and: "Bandwidth is reserved for protected path on involved isls"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { originIsl ->
            protectedIslsInfo.each { newIsl ->
                if (originIsl.id == newIsl.id) {
                    assert originIsl.availableBandwidth - newIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Switch some port to DOWN state on the protected path to init the recalculate procedure"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def islToBreakProtectedPath = protectedIsls.find { !currentIsls.contains(it) }
        northbound.portDown(islToBreakProtectedPath.dstSwitch.dpId, islToBreakProtectedPath.dstPort)

        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreakProtectedPath).get().state == IslChangeType.FAILED
        }

        then: "Protected path is recalculated"
        def newProtectedPath
        Wrappers.wait(WAIT_OFFSET) {
            newProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
            newProtectedPath != currentProtectedPath
        }

        and: "Bandwidth is reserved for new protected path on involved isls"
        def newProtectedIsls = pathHelper.getInvolvedIsls(newProtectedPath)
        def allLinks = northbound.getAllLinks()
        def newProtectedIslsInfo = newProtectedIsls.collect { islUtils.getIslInfo(allLinks, it).get() }

        allIsls.each { originIsl ->
            newProtectedIslsInfo.each { newIsl ->
                if (originIsl.id == newIsl.id) {
                    assert originIsl.availableBandwidth - newIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        and: "Reservation is deleted on the broken isl"
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
    def "Unable to create protected path on #flowDescription flow on the same path"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        } ?: assumeTrue("No suiting switches found", false)

        and: "A flow without protected path"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow1.allocateProtectedPath = false
        flowHelper.addFlow(flow1)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path.findAll { it != flow1 }.unique {
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
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow2.allocateProtectedPath = true
        flow2.maximumBandwidth = bandwidth
        flowHelper.addFlow(flow2)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        //TODO investigate error message
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found. " +
                "Failed to find path with requested bandwidth=$bandwidth: " +
                "Switch $srcSwitch.dpId doesn't have links with enough bandwidth"

        and: "Restore topology, delete flows and reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow1.id)
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
    def "Unable to enable protected path on #flowDescription flow on the same path"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        } ?: assumeTrue("No suiting switches found", false)

        and: "A flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flow.maximumBandwidth = bandwidth
        flowHelper.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path.findAll { it != flow }.unique {
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
                "Could not update flow: Not enough bandwidth found or path not found. " +
                "Failed to find path with requested bandwidth=$bandwidth: " +
                "Switch $srcSwitch.dpId doesn't have links with enough bandwidth"

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

    def "Able to swap flow path"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def (Switch srcSwitch, Switch dstSwitch) = getNotNeighboringSwitchPair(2)

        and: "Create a flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        and: "Needed rules exist"
        def mainFlowPath = northbound.getFlowPath(flow.id).forwardPath
        def protectedFlowPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        def commonNodeIds = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)
        Wrappers.wait(WAIT_OFFSET) {
            verifyRulesOnCommonNodes(commonNodeIds, srcSwitch.dpId, dstSwitch.dpId, mainFlowPath, protectedFlowPath)
        }

        def uniqueNodes = protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        }
        verifyRulesOnUniqueSwitches(uniqueNodes)

        when: "Swap flow path"
        def currentLastUpdate = northbound.getFlow(flow.id).lastUpdated
        northbound.swapFlowPath(flow.id)

        then: "Flow path is swapped"
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }
        def newCurrentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def newCurrentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        newCurrentPath != currentPath
        newCurrentPath == currentProtectedPath
        newCurrentProtectedPath != currentProtectedPath
        newCurrentProtectedPath == currentPath

        def newCurrentLastUpdate = northbound.getFlow(flow.id).lastUpdated
        //TODO should be '<' awaiting fix from dev
        currentLastUpdate == newCurrentLastUpdate

        and: "Rules are updated"
        def newMainFlowPath = northbound.getFlowPath(flow.id).forwardPath
        def newProtectedFlowPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        def newCommonNodeIds = newMainFlowPath*.switchId.intersect(newProtectedFlowPath*.switchId)
        Wrappers.wait(WAIT_OFFSET) {
            verifyRulesOnCommonNodes(newCommonNodeIds, srcSwitch.dpId, dstSwitch.dpId,
                    newMainFlowPath, newProtectedFlowPath)
        }

        def newUniqueNodes = protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        }
        verifyRulesOnUniqueSwitches(newUniqueNodes)

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to swap a flow in case allocate_protected_path=false"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "A flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)

        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Try to swap flow without protected path"
        northbound.swapFlowPath(flow.id)

        then: "Human readable error is returned"
        def exc = thrown(HttpServerErrorException)
        exc.rawStatusCode == 500
        //TODO awaiting fix from dev
//        def exc = thrown(HttpClientErrorException)
//        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not swap paths: Flow $flow.id doesn't have protected path"

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to swap a non-existent flow"() {
        when: "Try to swap path on a non-existent flow"
        northbound.swapFlowPath(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not swap paths: Flow $NON_EXISTENT_FLOW_ID not found"

    }

    def "Unable to swap an inactive flow"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            database.getPaths(src.dpId, dst.dpId)*.path.collect {
                pathHelper.getInvolvedIsls(it)
            }.unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        } ?: assumeTrue("No suiting switches found", false)

        and: "A flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        List<PathNode> broughtDownPorts = []
        database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path.findAll { it != flow }.unique {
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

        when: "Switch some port to DOWN state for changing the flow state to down"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        northbound.portDown(protectedIsls[0].dstSwitch.dpId, protectedIsls[0].dstPort)
        northbound.portDown(currentIsls[0].dstSwitch.dpId, currentIsls[0].dstPort)

        then: "Flow state is changed to down"
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Try to swap the inactive flow"
        northbound.swapFlowPath(flow.id)

        then: "Human readable error is returned"
        def exc = thrown(HttpServerErrorException)
        exc.rawStatusCode == 500
//        def exc = thrown(HttpClientErrorException)
//        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not swap paths: Protected flow path $flow.id is not in ACTIVE state"

        and: "Restore topology, delete flows and reset costs"
        //TODO investigate I think I have choosen switch pair incorrectly
        // possibly i have to choose switch pair without a-switch
        northbound.portUp(currentIsls[0].srcSwitch.dpId, currentIsls[0].srcPort)
        northbound.portUp(currentIsls[0].dstSwitch.dpId, currentIsls[0].dstPort)
        Wrappers.wait(WAIT_OFFSET * 2) { northbound.getFlowStatus(flow.id).status == FlowState.UP }
        northbound.portUp(protectedIsls[0].srcSwitch.dpId, protectedIsls[0].srcPort)
        northbound.portUp(protectedIsls[0].dstSwitch.dpId, protectedIsls[0].dstPort)
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    List<Switch> getNotNeighboringSwitchPair(minPossiblePath) {
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            allLinks.every { link ->
                !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId)
            } && possibleFlowPaths.size() >= minPossiblePath
        } ?: assumeTrue("No suiting switches found", false)

        return [srcSwitch, dstSwitch]
    }

    List<FlowEntry> filterRules(List<FlowEntry> rules, PathNodePayload node, Boolean ingress, Boolean egress) {
        if (ingress) {
            rules = rules.findAll {
                it.match.inPort == node.inputPort.toString() &&
                        it.instructions.applyActions.flowOutput == node.outputPort.toString()
            }
        }
        if (egress) {
            rules = rules.findAll {
                it.instructions?.applyActions?.flowOutput == node.inputPort.toString() &&
                        it.match.inPort == node.outputPort.toString()
            }
        }

        return rules
    }

    void verifyRulesOnCommonNodes(List<SwitchId> nodeIds, SwitchId srcSwitchId, SwitchId dstSwitchId,
                                  List<PathNodePayload> mainFlowPath, List<PathNodePayload> protectedFlowPath) {
        nodeIds.each { sw ->
            def rules = northbound.getSwitchRules(sw).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            if (sw == srcSwitchId || sw == dstSwitchId) {
                assert rules.size() == 3

                def mainNode = mainFlowPath.find { it.switchId == sw }
                assert filterRules(rules, mainNode, true, false).size() == 1
                assert filterRules(rules, mainNode, false, true).size() == 1
                def protectedNode = protectedFlowPath.find { it.switchId == sw }
                //protected path creates an egress rule only
                if (protectedNode.switchId == srcSwitchId) {
                    assert filterRules(rules, protectedNode, true, false).size() == 0
                    assert filterRules(rules, protectedNode, false, true).size() == 1
                } else {
                    assert filterRules(rules, protectedNode, true, false).size() == 1
                    assert filterRules(rules, protectedNode, false, true).size() == 0
                }
            } else {
                assert rules.size() == 4

                def mainNode = mainFlowPath.find { it.switchId == sw }
                assert filterRules(rules, mainNode, true, false).size() == 1
                assert filterRules(rules, mainNode, false, true).size() == 1

                def protectedNode = protectedFlowPath.find { it.switchId == sw }
                assert filterRules(rules, protectedNode, true, false).size() == 1
                assert filterRules(rules, protectedNode, false, true).size() == 1
            }
        }
    }

    void verifyRulesOnUniqueSwitches(List<PathNodePayload> nodes) {
        nodes.each { sw ->
            def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            assert rules.size() == 2

            assert filterRules(rules, sw, true, false).size() == 1
            assert filterRules(rules, sw, false, true).size() == 1
        }
    }

//    test new API swap, no any mention about it in ticket
//    update chaosSpec
//    A-B-C/A=B=C will the protected path be created? - done (unable to create protectedPath on the same path)
//    What is the correct behaviour in case isl is down on the protected path and new protected path can't be found
//    update chaosSpec
//    test isl/switch maintenance
//    run and update tests related to the validateRule action
//    run and update tests related to the synchronize action
//    run and update tests related to the flow validate action
//    port anti flap ??
//    VLAN=0 - done
//    extend flow validation tests to show their ability to detect problems in protected paths
//    unable create protected path on the same path - done
//    error message/code will be fixed - done
//    ignore preferable path and reroute to protected path - done
}

