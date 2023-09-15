package org.openkilda.functionaltests.spec.network

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import spock.lang.See

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.FlowEncapsulationType.VXLAN
import static org.openkilda.model.PathComputationStrategy.COST

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/path-validation/path-validation.md")
class PathCheckSpec extends HealthCheckSpecification {

    private static final String PCE_PATH_COMPUTATION_SUCCESS_MESSAGE = "The path has been computed successfully"
    @Tidy
    @Tags(SMOKE)
    def "No path validation errors for valid path without limitations"() {
        given: "Path for non-neighbouring switches"
        def path = topologyHelper.getAllSwitchPairs().nonNeighbouring().random()
                .getPaths().sort { it.size() }.first()

        when: "Check the path without limitations"
        def pathCheckResult = pathHelper.getPathCheckResult(path)

        then: "Path check result doesn't have errors"
        verifyAll(pathCheckResult) {
            getErrors().isEmpty()
            getPceResponse() == PCE_PATH_COMPUTATION_SUCCESS_MESSAGE
        }

    }

    @Tidy
    @Tags(SMOKE)
    def "Path check errors returned for each segment and each type of problem"() {
        given: "Path of at least three switches"
        def switchPair = topologyHelper.getAllSwitchPairs().nonNeighbouring().random()
        def path = switchPair.getPaths()
                .sort { it.size() }
                .first()
        def srcSwitch = switchPair.getSrc()

        and: "Source switch supports only Transit VLAN encapsulation"
        def backupSwitchProperties = switchHelper.getCachedSwProps(srcSwitch.getDpId())
        switchHelper.updateSwitchProperties(srcSwitch, backupSwitchProperties.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [TRANSIT_VLAN.toString()]
        })

        when: "Check the path where one switch doesn't support flow encapsulation type and all links don't have enough BW"
        def pathCheckResult = pathHelper.getPathCheckResult(path, Long.MAX_VALUE, VXLAN)

        then: "Path check result has multiple lack of bandwidth errors and at least one encapsulation support one"
        verifyAll(pathCheckResult) {
            !getErrors().findAll { it == "The switch ${srcSwitch.getDpId()} doesn\'t support the encapsulation type VXLAN." }.isEmpty()
            getErrors().findAll { it.contains("not enough bandwidth") }.size() ==
                    pathHelper.getInvolvedIsls(path).size() * 2
            getPceResponse().contains("Failed to find path with requested bandwidth")
        }

        cleanup:
        switchHelper.updateSwitchProperties(srcSwitch, backupSwitchProperties)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Latency check errors are returned for the whole existing flow"() {
        given: "Path of at least three switches"
        def switchPair = topologyHelper.getAllSwitchPairs().nonNeighbouring().random()
        def path = switchPair.getPaths()
                .sort { it.size() }
                .first()

        and: "Flow with cost computation strategy on that path"
        withPool {
            switchPair.paths.findAll { it != path }.eachParallel { pathHelper.makePathMorePreferable(path, it) }
        }
        def flow = flowHelperV2.addFlow(
                flowHelperV2.randomFlow(switchPair, false).tap { it.pathComputationStrategy = COST })

        when: "Check the path (equal to the flow) if the computation strategy would be LATENCY and max_latency would be too low"
        def pathCheckResult = pathHelper.getPathCheckResult(path, flow.getFlowId(), 1, 2)

        then: "Path check result returns latency validation errors (1 per tier1 and tier 2, per forward and revers paths)"
        verifyAll(pathCheckResult) {
            getErrors().findAll { it.contains("Requested latency is too low") }.size() == 2
            getErrors().findAll { it.contains("Requested latency tier 2 is too low") }.size() == 2
            getPceResponse().contains(
                    "Latency limit: Requested path must have latency 2ms or lower, but best path has latency")
        }

        cleanup:
        pathHelper."remove ISL properties artifacts after manipulating paths weights"()
        Wrappers.silent { flowHelperV2.deleteFlow(flow.getFlowId()) }
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Path intersection check errors are returned for each segment of existing flow"() {
        given: "Switch pair with two paths having minimal amount of intersecting segment"
        def switchPair = topologyHelper.getAllSwitchPairs().sortedBySmallestPathsAmount().first()
        def (flowPath, intersectingPath) = switchPair.paths.subsequences().findAll {
            it.size() == 2 //all combinations of path pairs
                    && [1, 2].contains(it[0].intersect(it[1]).size())
        } //where path pair has one or two common segments
                .first()

        and: "Flow on the chosen path"
        withPool {
            switchPair.paths.findAll { it != flowPath }.eachParallel { pathHelper.makePathMorePreferable(flowPath, it) }
        }
        def flow = flowHelperV2.addFlow(
                flowHelperV2.randomFlow(switchPair, false))

        when: "Check if the potential path has intersections with existing one"
        def pathCheckResult = pathHelper.getPathCheckResult(intersectingPath, flow.getFlowId())

        then: "Path check reports expected amount of intersecting segments"
        def expectedIntersectionCheckErrors = pathHelper.convertToPathNodePayload(flowPath).intersect(pathHelper.convertToPathNodePayload(intersectingPath)).size()
        verifyAll (pathCheckResult) {
            getErrors().findAll { it.contains("The following segment intersects with the flow ${flow.getFlowId()}") }.size()
                    == expectedIntersectionCheckErrors
            getPceResponse() == PCE_PATH_COMPUTATION_SUCCESS_MESSAGE
        }

        cleanup:
        pathHelper."remove ISL properties artifacts after manipulating paths weights"()
        Wrappers.silent { flowHelperV2.deleteFlow(flow.getFlowId()) }
    }
}
