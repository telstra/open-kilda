package org.openkilda.functionaltests.spec.flows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.See

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/reroute")
@Narrative("Verify that on-demand reroute operations are performed accurately.")
class IntentionalRerouteV2Spec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Tidy
    def "Not able to reroute to a path with not enough bandwidth available"() {
        given: "A flow with alternate paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = 10000
        flowHelperV2.addFlow(flow)

        def currentPathDto = northbound.getFlowPath(flow.flowId)
        def currentPath = PathHelper.convert(currentPathDto)
        def currentPathNodesV2 = PathHelper.convertToNodesV2(currentPathDto)

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = switchPair.paths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def changedIsls = alternativePaths.collect { altPath ->
            def thinIsl = pathHelper.getInvolvedIsls(altPath).find {
                !currentIsls.contains(it) && !currentIsls.contains(it.reversed)
            }
            def newBw = flow.maximumBandwidth - 1
            [thinIsl, thinIsl.reversed].each {
                database.updateIslMaxBandwidth(it, newBw)
                database.updateIslAvailableBandwidth(it, newBw)
            }
            thinIsl
        }

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        then: "The flow is NOT rerouted because of not enough bandwidth on alternative paths"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }
        !rerouteResponse.rerouted
        rerouteResponse.path.nodes == currentPathNodesV2

        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath

        cleanup: "Remove the flow, restore the bandwidth on ISLs, reset costs"
        flowHelperV2.deleteFlow(flow.flowId)
        changedIsls.each {
            database.resetIslBandwidth(it)
            database.resetIslBandwidth(it.reversed)
        }
    }

    @Tidy
    def "Able to reroute to a better path if it has enough bandwidth"() {
        given: "A flow with alternate paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = 10000
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flowHelperV2.addFlow(flow)
        def currentPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "Make one of the alternative paths to be the most preferable among all others"
        def preferableAltPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != preferableAltPath }.each {
            pathHelper.makePathMorePreferable(preferableAltPath, it)
        }

        and: "Make the future path to have exact bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def thinIsl = pathHelper.getInvolvedIsls(preferableAltPath).find {
            !currentIsls.contains(it) && !currentIsls.contains(it.reversed)
        }
        [thinIsl, thinIsl.reversed].each {
            database.updateIslMaxBandwidth(it, flow.maximumBandwidth)
            database.updateIslAvailableBandwidth(it, flow.maximumBandwidth)
        }

        and: "Init a reroute of the flow"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        then: "The flow is successfully rerouted and goes through the preferable path"
        def pathDto = northbound.getFlowPath(flow.flowId)
        def newPath = PathHelper.convert(pathDto)
        def newPathNodesV2 = PathHelper.convertToNodesV2(pathDto)

        rerouteResponse.rerouted
        rerouteResponse.path.nodes == newPathNodesV2

        newPath == preferableAltPath
        pathHelper.getInvolvedIsls(newPath).contains(thinIsl)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(thinIsl).get().availableBandwidth == 0 }

        and: "Flow statistic is working"
        statsHelper.verifyFlowWritesStats(flow.flowId)

        cleanup: "Remove the flow, restore bandwidths on ISLs, reset costs"
        flowHelperV2.deleteFlow(flow.flowId)
        [thinIsl, thinIsl.reversed].each { database.resetIslBandwidth(it) }
    }

    /**
     * Select a longest available path between 2 switches, then reroute to another long path. Run traffexam during the
     * reroute and expect no packet loss.
     */
    @Tidy
    @Tags(HARDWARE)
    def "Intentional flow reroute is not causing any packet loss"() {
        given: "An unmetered flow going through a long not preferable path(reroute potential)"
        //will be available on virtual as soon as we get the latest iperf installed in lab-service images
        assumeTrue("There should be at least two active traffgens for test execution",
                topology.activeTraffGens.size() >= 2)

        def src = topology.activeTraffGens[0].switchConnected
        def dst = topology.activeTraffGens[1].switchConnected
        //first adjust costs to use the longest possible path between switches
        List<List<PathNode>> allPaths = database.getPaths(src.dpId, dst.dpId)*.path
        def longestPath = allPaths.max { it.size() }
        def changedIsls = allPaths.findAll { it != longestPath }
                .collect { pathHelper.makePathMorePreferable(longestPath, it) }
        //and create the flow that uses the long path
        def flow = flowHelperV2.randomFlow(src, dst)
        flow.maximumBandwidth = 0
        flow.ignoreBandwidth = true
        flowHelperV2.addFlow(flow)
        assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == longestPath
        //now make another long path more preferable, for reroute to rebuild the rules on other switches in the future
        northbound.updateLinkProps((changedIsls + changedIsls*.reversed)
                .collect { islUtils.toLinkProps(it, [cost: DEFAULT_COST.toString()]) })
        def potentialNewPath = allPaths.findAll { it != longestPath }.max { it.size() }
        allPaths.findAll { it != potentialNewPath }.each { pathHelper.makePathMorePreferable(potentialNewPath, it) }

        when: "Start traffic examination"
        def traffExam = traffExamProvider.get()
        def bw = 100000 // 100 Mbps
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flowHelperV2.toV1(flow), bw)
        [exam.forward, exam.reverse].each { direction ->
            def resources = traffExam.startExam(direction, true)
            direction.setResources(resources)
        }

        and: "While traffic flow is active, request a flow reroute"
        [exam.forward, exam.reverse].each { assert !traffExam.isFinished(it) }
        def reroute = northboundV2.rerouteFlow(flow.flowId)

        then: "Flow is rerouted"
        reroute.rerouted
        expect reroute.path.nodes, sameBeanAs(PathHelper.convertToNodesV2(potentialNewPath))
                .ignoring("segmentLatency")
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Traffic examination result shows acceptable packet loss percentage"
        def examReports = [exam.forward, exam.reverse].collect { traffExam.waitExam(it) }
        examReports.each {
            //Minor packet loss is considered a measurement error and happens regardless of reroute
            assert it.consumerReport.lostPercent < 1
        }

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Able to reroute to a path with not enough bandwidth available in case ignoreBandwidth=true"() {
        given: "A flow with alternate paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = 10000
        flow.ignoreBandwidth = true
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flowHelperV2.addFlow(flow)
        def currentPathDto = northbound.getFlowPath(flow.flowId)
        def currentPath = PathHelper.convert(currentPathDto)
        def currentPathNodesV2 = PathHelper.convertToNodesV2(currentPathDto)

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = switchPair.paths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newBw = flow.maximumBandwidth - 1
        def changedIsls = alternativePaths.collect { altPath ->
            def thinIsl = pathHelper.getInvolvedIsls(altPath).find {
                !currentIsls.contains(it) && !currentIsls.contains(it.reversed)
            }
            [thinIsl, thinIsl.reversed].each {
                database.updateIslMaxBandwidth(it, newBw)
                database.updateIslAvailableBandwidth(it, newBw)
            }
            thinIsl
        }

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        then: "The flow is rerouted because ignoreBandwidth=true"
        rerouteResponse.rerouted
        rerouteResponse.path.nodes != currentPathNodesV2

        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        def updatedPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))
        updatedPath != currentPath
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Available bandwidth was not changed while rerouting due to ignoreBandwidth=true"
        def allLinks = northbound.getAllLinks()
        changedIsls.each {
            islUtils.getIslInfo(allLinks, it).each {
                assert it.value.availableBandwidth == newBw
            }
        }

        cleanup: "Remove the flow, restore the bandwidth on ISLs, reset costs"
        flowHelperV2.deleteFlow(flow.flowId)
        changedIsls.each {
            database.resetIslBandwidth(it)
            database.resetIslBandwidth(it.reversed)
        }
    }

    @Tidy
    @Ignore("https://github.com/telstra/open-kilda/issues/2996")
    @Tags(HARDWARE)
    def "Intentional flow reroute with VXLAN encapsulation is not causing any packet loss"() {
        given: "A vxlan flow"
        def allTraffgenSwitchIds = topology.activeTraffGens*.switchConnected.findAll {
            it.noviflow && !it.wb5164
        }*.dpId ?: assumeTrue("Should be at least two active traffgens connected to NoviFlow switches", false)
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { swP ->
            allTraffgenSwitchIds.contains(swP.src.dpId) && allTraffgenSwitchIds.contains(swP.dst.dpId) &&
                    swP.paths.findAll { path ->
                        pathHelper.getInvolvedSwitches(path).every { it.noviflow  && !it.wb5164 }
                    }.size() > 1
        } ?: assumeTrue("Unable to find required switches/paths in topology",false)
        def availablePaths = switchPair.paths.findAll { pathHelper.getInvolvedSwitches(it).find { it.noviflow }}

        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = 0
        flow.ignoreBandwidth = true
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(flow)
        def altPaths = availablePaths.findAll { it != pathHelper.convert(northbound.getFlowPath(flow.flowId)) }
        def potentialNewPath = altPaths[0]
        availablePaths.findAll { it != potentialNewPath }.each { pathHelper.makePathMorePreferable(potentialNewPath, it) }

        when: "Start traffic examination"
        def traffExam = traffExamProvider.get()
        def bw = 100000 // 100 Mbps
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flowHelperV2.toV1(flow), bw)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction, true)
                direction.setResources(resources)
            }
        }

        and: "While traffic flow is active, request a flow reroute"
        [exam.forward, exam.reverse].each { assert !traffExam.isFinished(it) }
        def reroute = northboundV2.rerouteFlow(flow.flowId)

        then: "Flow is rerouted"
        reroute.rerouted
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Traffic examination result shows acceptable packet loss percentage"
        def examReports = [exam.forward, exam.reverse].collect { traffExam.waitExam(it) }
        examReports.each {
            //Minor packet loss is considered a measurement error and happens regardless of reroute
            assert it.consumerReport.lostPercent < 1
        }

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def cleanup() {
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
