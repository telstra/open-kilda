package org.openkilda.functionaltests.spec.network

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.FlowEncapsulationType.VXLAN

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.info.event.PathNode

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/path-validation/path-validation.md")

class PathCheckSpec extends HealthCheckSpecification {

    private static final String PCE_PATH_COMPUTATION_SUCCESS_MESSAGE = "The path has been computed successfully"

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags(SMOKE)
    def "No path validation errors for valid path without limitations"() {
        given: "Path for non-neighbouring switches"
        def path = switchPairs.all().nonNeighbouring().random()
                .getPaths().sort { it.size() }.first()

        when: "Check the path without limitations"
        def pathCheckResult = pathHelper.getPathCheckResult(path)

        then: "Path check result doesn't have validation messages"
        verifyAll(pathCheckResult) {
            getValidationMessages().isEmpty()
            getPceResponse() == PCE_PATH_COMPUTATION_SUCCESS_MESSAGE
        }

    }

    @Tags(SMOKE)
    def "Path check errors returned for each segment and each type of problem"() {
        given: "Path of at least three switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()
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
            !getValidationMessages().findAll { it == "The switch ${srcSwitch.getDpId()} doesn\'t support the encapsulation type VXLAN." }.isEmpty()
            getValidationMessages().findAll { it.contains("not enough bandwidth") }.size() ==
                    pathHelper.getInvolvedIsls(path).size() * 2
            getPceResponse().contains("Failed to find path with requested bandwidth")
        }
    }

    @Tags(LOW_PRIORITY)
    def "Latency check errors are returned for the whole existing flow"() {
        given: "Path of at least three switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def path = switchPair.getPaths()
                .sort { it.size() }
                .first()

        and: "Flow with cost computation strategy on that path"
        withPool {
            switchPair.paths.findAll { it != path }.eachParallel { pathHelper.makePathMorePreferable(path, it) }
        }
        def flow = flowFactory.getBuilder(switchPair, false)
                .withPathComputationStrategy(PathComputationStrategy.COST).build()
                .create()

        when: "Check the path (equal to the flow) if the computation strategy would be LATENCY and max_latency would be too low"
        def pathCheckResult = pathHelper.getPathCheckResult(path, flow.flowId, 1, 2)

        then: "Path check result returns latency validation errors (1 per tier1 and tier 2, per forward and revers paths)"
        verifyAll(pathCheckResult) {
            getValidationMessages().findAll { it.contains("Requested latency is too low") }.size() == 2
            getValidationMessages().findAll { it.contains("Requested latency tier 2 is too low") }.size() == 2
            getPceResponse().contains(
                    "Latency limit: Requested path must have latency 2ms or lower, but best path has latency")
        }
    }

    @Tags(LOW_PRIORITY)
    def "Path intersection check errors are returned for each segment of existing flow"() {
        given: "Flow has been created successfully"
        def switchPair = switchPairs.all().nonNeighbouring().first()
        def flow = flowFactory.getRandom(switchPair, false)
        def flowPathDetails = flow.retrieveAllEntityPaths()

        and: "Path with intersected segment(s) for verification has been collected"
        def flowForwardPath = flowPathDetails.getPathNodes()
        //at least one common ISl
        def intersectingPath = switchPair.paths.findAll { it.size() > 4 && it.intersect(flowForwardPath).size() > 1 }.first()

        and: "Involved ISls have been collected"
        def flowInvolvedISLs = flowPathDetails.flowPath.getInvolvedIsls(Direction.FORWARD) + flowPathDetails.flowPath.getInvolvedIsls(Direction.REVERSE)
        def intersectedPathInvolvedISLs = new Path(pathHelper.convertToPathNodePayload(intersectingPath), topology).getInvolvedIsls()
        def commonISLs = flowInvolvedISLs.intersect(intersectedPathInvolvedISLs)
        assert !commonISLs.isEmpty(), "Path for verification has no intersected segment(s) with the flow."

        when: "Check if the potential path has intersections with existing one"
        def pathCheckResult = pathHelper.getPathCheckResult(intersectingPath, flow.flowId)

        then: "Path check reports expected amount of intersecting segments"
        verifyAll (pathCheckResult) {
            getValidationMessages().findAll { it.contains("The following segment intersects with the flow ${flow.flowId}") }.size()
                    == commonISLs.size()
            getPceResponse() == PCE_PATH_COMPUTATION_SUCCESS_MESSAGE
        }
    }

    @Tags(LOW_PRIORITY)
    def "Path intersection check errors are returned for each segment of each flow in diverse group"() {
        given: "List of required neighbouring switches has been collected"
        def firstSwitchPair = switchPairs.all().neighbouring().random()
        def secondSwitchPair = switchPairs.all().neighbouring().excludePairs([firstSwitchPair])
                .includeSwitch(firstSwitchPair.dst).random()

        and:"Two flows in one diverse group have been created"
        def flow1 = flowFactory.getRandom(firstSwitchPair, false)
        def flow2 = flowFactory.getBuilder(secondSwitchPair, false)
                .withDiverseFlow(flow1.flowId).build()
                .create()

        and: "Paths for both flows have been collected"
        def flow1Path = flow1.retrieveAllEntityPaths().getPathNodes()
        def flow2Path = flow2.source.switchId == flow1.destination.switchId ?
                flow2.retrieveAllEntityPaths().getPathNodes(Direction.FORWARD) :
                flow2.retrieveAllEntityPaths().getPathNodes(Direction.REVERSE)

        when: "Check potential path that has NO intersection with both flows from diverse group"
        LinkedList<PathNode> pathToCheck = switchPairs.all().neighbouring().excludePairs([firstSwitchPair, secondSwitchPair])
                .includeSwitch(firstSwitchPair.src).random().paths.first()

        if(pathToCheck.last().switchId != firstSwitchPair.src.dpId) {
            pathToCheck = pathToCheck.reverse()
        }

        then: "Path check reports has No validation error about intersection"
        verifyAll {
            pathHelper.getPathCheckResult(pathToCheck, flow1.flowId).getValidationMessages().isEmpty()
            pathHelper.getPathCheckResult(pathToCheck, flow2.flowId).getValidationMessages().isEmpty()
        }

        when: "Check potential path that has intersection ONLY with one flow from diverse group"
        pathToCheck.addAll(flow1Path)
        def checkErrors = pathHelper.getPathCheckResult(pathToCheck, flow1.flowId)

        then: "Path check reports has ONLY one intersecting segment"
        verifyAll{
            checkErrors.getValidationMessages().size() == 1
            checkErrors.getValidationMessages().find { it.contains"The following segment intersects with the flow ${flow1.flowId}" }
        }

        when: "Check potential path that has intersection with both flows from diverse group"
        pathToCheck.addAll(flow2Path)
        checkErrors = pathHelper.getPathCheckResult(pathToCheck, flow1.flowId)

        then: "Path check reports has intersecting segments with both flows from diverse group"
        verifyAll {
            checkErrors.getValidationMessages().size() == 2
            checkErrors.getValidationMessages().find { it.contains"The following segment intersects with the flow ${flow1.flowId}" }
            checkErrors.getValidationMessages().find { it.contains"The following segment intersects with the flow ${flow2.flowId}" }
        }
    }
}
