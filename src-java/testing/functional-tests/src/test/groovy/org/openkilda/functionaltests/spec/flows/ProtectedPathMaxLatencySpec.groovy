package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.model.PathComputationStrategy
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared
import spock.lang.Unroll

class ProtectedPathMaxLatencySpec extends HealthCheckSpecification {
    @Shared List<PathNode> mainPath, protectedPath
    @Shared List<Isl> mainIsls, protectedIsls, islsToBreak
    @Shared SwitchPair switchPair

    def setupOnce() {
        //setup: Two active switches with two diverse paths
        List<List<PathNode>> paths
        switchPair = topologyHelper.switchPairs.find {
            paths = it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            paths.size() >= 2
        } ?: assumeTrue("No suiting switches found", false)
        mainPath = paths[0]
        protectedPath = paths[1]
        mainIsls = pathHelper.getInvolvedIsls(mainPath)
        protectedIsls = pathHelper.getInvolvedIsls(protectedPath)
        //deactivate other paths for more clear experiment
        def isls = mainIsls + protectedIsls
        islsToBreak = switchPair.paths.findAll { !paths.contains(it) }
                                .collect { pathHelper.getInvolvedIsls(it).find {!isls.contains(it) && !isls.contains(it.reversed) } }
                                .unique { [it, it.reversed].sort() }
        islsToBreak.each { antiflap.portDown(it.srcSwitch.dpId, it.srcPort) }
    }

    @Tidy
    def "Able to create protected flow with max_latency strategy if both paths satisfy SLA"() {
        given: "2 non-overlapping paths with 10 and 9 latency"
        setLatencyForPaths(10, 9)

        when: "Create a flow with protected path and max_latency 11"
        def flow = flowHelperV2.randomFlow(switchPair).tap {
            allocateProtectedPath = true
            maxLatency = 11
            pathComputationStrategy = PathComputationStrategy.MAX_LATENCY.toString()
        }
        flowHelperV2.addFlow(flow)

        then: "Flow is created, main path is the 10 latency path, protected is 9 latency"
        def path = northbound.getFlowPath(flow.flowId)
        pathHelper.convert(path) == mainPath
        pathHelper.convert(path.protectedPath) == protectedPath

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Unroll
    @Tags([LOW_PRIORITY])
    def "Unable to create protected flow with max_latency strategy if #condition"() {
        given: "2 non-overlapping paths with 10 and 9 latency"
        setLatencyForPaths(10, 9)

        when: "Create a flow with protected path and max_latency #testMaxLatency"
        def flow = flowHelperV2.randomFlow(switchPair).tap {
            allocateProtectedPath = true
            maxLatency = testMaxLatency
            pathComputationStrategy = PathComputationStrategy.MAX_LATENCY.toString()
        }
        flowHelperV2.addFlow(flow)

        then: "Flow is not created, error returned describing that no paths found"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        def errorDetails = e.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription.startsWith("Not enough bandwidth or no path found. Failed to find path")

        cleanup:
        !e && flowHelperV2.deleteFlow(flow.flowId)

        where:
        testMaxLatency  | condition
        10              | "only 1 path satisfies SLA"
        9               | "both paths do not satisfy SLA"
    }

    def setLatencyForPaths(int mainPathLatency, int protectedPathLatency) {
        def nanoMultiplier = 1000000
        def mainIslCost = mainPathLatency.intdiv(mainIsls.size()) * nanoMultiplier
        def protectedIslCost = protectedPathLatency.intdiv(protectedIsls.size()) * nanoMultiplier
        [mainIsls[0], mainIsls[0].reversed].each {
            database.updateIslLatency(it, mainIslCost + (mainPathLatency % mainIsls.size()) * nanoMultiplier) }
        mainIsls.tail().each {[it, it.reversed].each { database.updateIslLatency(it, mainIslCost) } }
        [protectedIsls[0], protectedIsls[0].reversed].each {
            database.updateIslLatency(it, protectedIslCost + (protectedPathLatency % protectedIsls.size()) * nanoMultiplier) }
        protectedIsls.tail().each {[it, it.reversed].each { database.updateIslLatency(it, protectedIslCost) } }
    }

    def cleanupSpec() {
        islsToBreak.each { getAntiflap().portUp(it.srcSwitch.dpId, it.srcPort) }
        Wrappers.wait(getDiscoveryInterval() + WAIT_OFFSET) {
            assert getNorthbound().getActiveLinks().size() == getTopology().islsForActiveSwitches.size() * 2
        }
        getDatabase().resetCosts()
    }
}
