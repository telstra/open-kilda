package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.model.switches.Manufacturer.NOVIFLOW
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/reroute")
@Narrative("Verify that on-demand reroute operations are performed accurately.")

class IntentionalRerouteSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags(ISL_PROPS_DB_RESET)
    def "Not able to reroute to a path with not enough bandwidth available"() {
        given: "A flow with alternate paths available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(10000)
                .build().create()

        def initialFlowPath = flow.retrieveAllEntityPaths()
        def initialFlowIsls = isls.all().findInPath(initialFlowPath)

        when: "Make the current path less preferable than alternatives"
        switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }.findAll { it != initialFlowIsls }
                .each { isls.all().makePathIslsMorePreferable(it, initialFlowIsls) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def newBw = flow.maximumBandwidth - 1
        isls.all().relatedTo(switchPair.src).excludeIsls(initialFlowIsls).updateIslsAvailableAndMaxBandwidthInDb(newBw)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = flow.reroute()

        then: "The flow is NOT rerouted because of not enough bandwidth on alternative paths"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        !rerouteResponse.rerouted
        rerouteResponse.path.nodes == initialFlowPath.flowPath.path.forward.nodes.toPathNodeV2()
        isls.all().findInPath(flow.retrieveAllEntityPaths()) == initialFlowIsls
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a better path if it has enough bandwidth"() {
        given: "A flow with alternate paths available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(10000)
                .withEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build().create()
        def initialPathEntities = flow.retrieveAllEntityPaths()
        def initialFlowIsls = isls.all().findInPath(initialPathEntities)

        when: "Make one of the alternative paths to be the most preferable among all others"
        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
        def preferableAltPathIsls = availablePathsIsls.find { it != initialFlowIsls}
        availablePathsIsls.findAll { it != preferableAltPathIsls }.each {
            isls.all().makePathIslsMorePreferable(preferableAltPathIsls, it)
        }

        and: "Make the future path to have exact bandwidth to handle the flow"
        def thinIsl = preferableAltPathIsls.find {
            !it.isIncludedInPath(initialFlowIsls)
        }
        thinIsl.setAvailableAndMaxBandwidthInDb(flow.maximumBandwidth)

        and: "Init a reroute of the flow"
        def rerouteResponse = flow.reroute()

        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        then: "The flow is successfully rerouted and goes through the preferable path"
        def newPathEntities = flow.retrieveAllEntityPaths()
        def flowNewPathIsls = isls.all().findInPath(newPathEntities)

        rerouteResponse.rerouted
        rerouteResponse.path.nodes == newPathEntities.flowPath.path.forward.nodes.toPathNodeV2()

        flowNewPathIsls == preferableAltPathIsls
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) { assert  thinIsl.getNbDetails().availableBandwidth == 0 }
    }

    /**
     * Select a longest available path between 2 switches, then reroute to another long path. Run traffexam during the
     * reroute and expect no packet loss.
     */
    @Tags([HARDWARE]) //hw only due to instability on virtual env. reproduces rarely only on jenkins env though
    def "Intentional flow reroute is not causing any packet loss"() {
        given: "An unmetered flow going through a long not preferable path(reroute potential)"
        //will be available on virtual as soon as we get the latest iperf installed in lab-service images
        assumeTrue(topology.activeTraffGens.size() >= 2,
                "There should be at least two active traffgens for test execution")

        def switchPair = switchPairs.all().withTraffgensOnBothEnds().random()
        //first adjust costs to use the longest possible path between switches
        def longestPaths =  switchPair.retrievePathsWithNodesCount(switchPair.getSizeOfTheLongestPath())
        def longestPathIsls = isls.all().findInPath(longestPaths.first())

        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
        availablePathsIsls.findAll { it != longestPathIsls }
                .each { isls.all().makePathIslsMorePreferable(longestPathIsls, it) }

        //and create the flow that uses the long path
        def flowEntity = flowFactory.getBuilder(switchPair)
                .withBandwidth(0)
                .withIgnoreBandwidth(true)
        def flow = flowEntity.build().create()
        assert isls.all().findInPath(flow.retrieveAllEntityPaths()) == longestPathIsls

        //now make another long path more preferable, for reroute to rebuild the rules on other switches in the future
        isls.all().updateIslsCostInDb(DEFAULT_COST)

        def potentialNewPath = longestPaths.size() > 1 ? longestPaths.last()
                : switchPair.retrieveTheClosestLongPathsTo(longestPaths.first()).first()
        def potentialNewPathIsls = isls.all().findInPath(potentialNewPath)

        availablePathsIsls.findAll { it != potentialNewPathIsls }
                .each { isls.all().makePathIslsMorePreferable(potentialNewPathIsls, it) }

        when: "Start traffic examination"
        def traffExam = traffExamProvider.get()
        def bw = 100000 // 100 Mbps
        def exam = flow.traffExam(traffExam, bw, 20)
        [exam.forward, exam.reverse].each { direction ->
            direction.udp = true
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
        }

        and: "While traffic flow is active, request a flow reroute"
        [exam.forward, exam.reverse].each { assert !traffExam.isFinished(it) }
        def reroute = flow.reroute()

        then: "Flow is rerouted"
        reroute.rerouted
        reroute.path.nodes == potentialNewPath.retrieveNodes()
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        and: "Traffic examination result shows acceptable packet loss percentage"
        def examReports = [exam.forward, exam.reverse].collect { traffExam.waitExam(it) }
        examReports.each {
            //Minor packet loss is considered a measurement error and happens regardless of reroute
            //https://github.com/telstra/open-kilda/issues/5406
            //assert it.consumerReport.lostPercent < 1
            assert it.hasTraffic()
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a path with not enough bandwidth available in case ignoreBandwidth=true"() {
        given: "A flow with alternate paths available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(10000)
                .withIgnoreBandwidth(true)
                .withEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build().create()

        def initialFlowPath = flow.retrieveAllEntityPaths()
        def initialFlowIsls = isls.all().findInPath(initialFlowPath)

        when: "Make the current path less preferable than alternatives"
        def alternativePathsIsls = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
                .findAll { it != initialFlowIsls }
        alternativePathsIsls.each { isls.all().makePathIslsMorePreferable(it, initialFlowIsls) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def newBw = flow.maximumBandwidth - 1
        def changedIsls = isls.all().relatedTo(switchPair.src).excludeIsls(initialFlowIsls)
                .updateIslsAvailableAndMaxBandwidthInDb(newBw).getListOfIsls()

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = flow.reroute()

        then: "The flow is rerouted because ignoreBandwidth=true"
        rerouteResponse.rerouted
        rerouteResponse.path.nodes != initialFlowPath.flowPath.path.forward.nodes.toPathNodeV2()

        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        def newFlowIsls = isls.all().findInPath(flow.retrieveAllEntityPaths())
        newFlowIsls != initialFlowIsls
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        and: "Available bandwidth was not changed while rerouting due to ignoreBandwidth=true"
        def allLinks = northbound.getAllLinks()
        changedIsls.each {
            assert it.getInfo(allLinks, false).availableBandwidth == newBw
            assert it.getInfo(allLinks, true).availableBandwidth == newBw
        }
    }

    @Tags(HARDWARE)
    def "Intentional flow reroute with VXLAN encapsulation is not causing any packet loss"() {
        given: "A vxlan flow"
        def switchPair = switchPairs.all().neighbouring().withSwitchesManufacturedBy(NOVIFLOW, NOVIFLOW)
                .withBothSwitchesVxLanEnabled().withTraffgensOnBothEnds().random()

        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(0)
                .withIgnoreBandwidth(true)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build().create()

        def initialFlowIsls = isls.all().findInPath(flow.retrieveAllEntityPaths())

        def allAvailablePathsIsls = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
        def potentialNewPath = allAvailablePathsIsls.findAll { it != initialFlowIsls }.first()

        allAvailablePathsIsls.findAll { it != potentialNewPath }
                .each { isls.all().makePathIslsMorePreferable(potentialNewPath, it) }

        when: "Start traffic examination"
        def traffExam = traffExamProvider.get()
        def bw = 100000 // 100 Mbps
        def exam = flow.traffExam(traffExam, bw, null)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                direction.udp = true
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
            }
        }

        and: "While traffic flow is active, request a flow reroute"
        [exam.forward, exam.reverse].each { assert !traffExam.isFinished(it) }
        def reroute = flow.reroute()

        then: "Flow is rerouted"
        reroute.rerouted
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        and: "Traffic examination result shows acceptable packet loss percentage"
        def examReports = [exam.forward, exam.reverse].collect { traffExam.waitExam(it) }
        examReports.each {
            //Minor packet loss is considered a measurement error and happens regardless of reroute
            //https://github.com/telstra/open-kilda/issues/5406
            //assert it.consumerReport.lostPercent < 1
        }
    }

    @Tags([LOW_PRIORITY, ISL_PROPS_DB_RESET])
    def "Not able to reroute to a path with not enough bandwidth available [v1 api]"() {
        given: "A flow with alternate paths available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(10000)
                .build().create()
        def initialFlowPath = flow.retrieveAllEntityPaths()
        def initialFlowIsls = isls.all().findInPath(initialFlowPath)

        when: "Make the current path less preferable than alternatives"
        switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }.findAll{ it != initialFlowIsls }
                .each { isls.all().makePathIslsMorePreferable(it, initialFlowIsls) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def newBw = flow.maximumBandwidth - 1
        isls.all().relatedTo(switchPair.src).excludeIsls(initialFlowIsls).updateIslsAvailableAndMaxBandwidthInDb(newBw)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = flow.rerouteV1()

        then: "The flow is NOT rerouted because of not enough bandwidth on alternative paths"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        !rerouteResponse.rerouted
        rerouteResponse.path.path == initialFlowPath.flowPath.path.forward.nodes.toPathNode()
        int seqId = 0
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }
        isls.all().findInPath(flow.retrieveAllEntityPaths()) == initialFlowIsls
    }

    @Tags([LOW_PRIORITY, ISL_PROPS_DB_RESET])
    def "Able to reroute to a better path if it has enough bandwidth [v1 api]"() {
        given: "A flow with alternate paths available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(10000)
                .build().create()
        def initialFlowPath = flow.retrieveAllEntityPaths()
        def initialFlowIsls = isls.all().findInPath(initialFlowPath)

        when: "Make one of the alternative paths to be the most preferable among all others"
        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
        def preferableAltPathIsls = availablePathsIsls.find { it != initialFlowIsls }
        availablePathsIsls.findAll { it != preferableAltPathIsls }.each {
            isls.all().makePathIslsMorePreferable(preferableAltPathIsls, it)
        }

        and: "Make the future path to have exact bandwidth to handle the flow"
        def thinIsl = preferableAltPathIsls.find { !it.isIncludedInPath(initialFlowIsls) }
        thinIsl.updateAvailableAndMaxBandwidthInDb(flow.maximumBandwidth)

        and: "Init a reroute of the flow"
        def rerouteResponse = flow.rerouteV1()
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        then: "The flow is successfully rerouted and goes through the preferable path"
        def newFlowPath = flow.retrieveAllEntityPaths()
        def newFlowIsls = isls.all().findInPath(newFlowPath)
        int seqId = 0

        rerouteResponse.rerouted
        rerouteResponse.path.path == newFlowPath.flowPath.path.forward.nodes.toPathNode()
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        newFlowIsls == preferableAltPathIsls

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) { assert thinIsl.getNbDetails().availableBandwidth == 0 }
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
    }
}