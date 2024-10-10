package org.openkilda.functionaltests.spec.network

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.FlowEncapsulationType.VXLAN
import static org.openkilda.model.PathComputationStrategy.COST
import static org.openkilda.model.PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH
import static org.openkilda.model.PathComputationStrategy.LATENCY

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.payload.network.PathValidationPayload
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v2.flows.FlowPathV2.PathNodeV2

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
        def path = switchPairs.all().nonNeighbouring().random().retrieveAvailablePaths().shuffled().first()

        when: "Check the path without limitations"
        def validationPayload = PathValidationPayload.builder()
                .nodes(path.nodes.toPathNodePayload())
                .pathComputationStrategy(COST_AND_AVAILABLE_BANDWIDTH).build()

        def pathCheckResult = northboundV2.checkPath(validationPayload)

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
        def path = switchPair.retrieveAvailablePaths().first()
        def srcSwitch = switchPair.getSrc()

        and: "Source switch supports only Transit VLAN encapsulation"
        def backupSwitchProperties = switchHelper.getCachedSwProps(srcSwitch.getDpId())
        switchHelper.updateSwitchProperties(srcSwitch, backupSwitchProperties.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [TRANSIT_VLAN.toString()]
        })

        when: "Check the path where one switch doesn't support flow encapsulation type and all links don't have enough BW"
        def validationPayload = PathValidationPayload.builder()
                .nodes(path.nodes.toPathNodePayload())
                .bandwidth(Long.MAX_VALUE)
                .flowEncapsulationType(convertEncapsulationType(VXLAN))
                .pathComputationStrategy(COST_AND_AVAILABLE_BANDWIDTH).build()

        def pathCheckResult = northboundV2.checkPath(validationPayload)

        then: "Path check result has multiple lack of bandwidth errors and at least one encapsulation support one"
        verifyAll(pathCheckResult) {
            assert !getValidationMessages().findAll { it == "The switch ${srcSwitch.getDpId()} doesn\'t support the encapsulation type VXLAN." }.isEmpty()
            assert getValidationMessages().findAll { it.contains("not enough bandwidth") }.size() ==
                    path.getInvolvedIsls().size() * 2
            assert getPceResponse().contains("Failed to find path with requested bandwidth")
        }
    }

    @Tags(LOW_PRIORITY)
    def "Latency check errors are returned for the whole existing flow"() {
        given: "Path of at least three switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def availablePaths = switchPair.retrieveAvailablePaths()
        def path = availablePaths.first()
        def pathInvolvedIsls = path.getInvolvedIsls()

        and: "Flow with cost computation strategy on that path"
        availablePaths.collect{ it.getInvolvedIsls() }.findAll { !it.containsAll(pathInvolvedIsls) }
                .each { islHelper.makePathIslsMorePreferable(pathInvolvedIsls, it) }

        def flow = flowFactory.getBuilder(switchPair, false)
                .withPathComputationStrategy(PathComputationStrategy.COST).build()
                .create()

        when: "Check the path (equal to the flow) if the computation strategy would be LATENCY and max_latency would be too low"
        def validationPayload = PathValidationPayload.builder()
                .nodes(path.nodes.toPathNodePayload())
                .latencyMs(1)
                .latencyTier2ms(2)
                .reuseFlowResources(flow.flowId)
                .pathComputationStrategy(LATENCY).build()

        def pathCheckResult = northboundV2.checkPath(validationPayload)

        then: "Path check result returns latency validation errors (1 per tier1 and tier 2, per forward and revers paths)"
        verifyAll(pathCheckResult) {
            assert getValidationMessages().findAll { it.contains("Requested latency is too low") }.size() == 2
            assert getValidationMessages().findAll { it.contains("Requested latency tier 2 is too low") }.size() == 2
            assert getPceResponse().contains(
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
        def flowForwardPath = flowPathDetails.flowPath.path.forward.retrieveNodes()

        //at least one common ISl
        def intersectingPath = switchPair.retrieveAvailablePaths()
                .find { it.retrieveNodes().size() > 4 && it.retrieveNodes().intersect(flowForwardPath).size() > 1 }

        and: "Involved ISls have been collected"
        def flowInvolvedISLs = flowPathDetails.flowPath.getInvolvedIsls(Direction.FORWARD)
        def intersectedPathInvolvedISLs = intersectingPath.getInvolvedIsls()
        def commonISLs = flowInvolvedISLs.intersect(intersectedPathInvolvedISLs)
        assert !commonISLs.isEmpty(), "Path for verification has no intersected segment(s) with the flow."

        when: "Check if the potential path has intersections with existing one"
        def pathCheckResult = northboundV2.checkPath(generatePathPayload(intersectingPath, flow.flowId))

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
                .includeSourceSwitch(firstSwitchPair.dst).random()

        and:"Two flows in one diverse group have been created"
        def flow1 = flowFactory.getRandom(firstSwitchPair, false)
        def flow2 = flowFactory.getBuilder(secondSwitchPair, false)
                .withDiverseFlow(flow1.flowId).build()
                .create()

        and: "Paths for both flows have been collected"
        def flow1Path = flow1.retrieveAllEntityPaths().flowPath.path.forward
        def flow2Path = flow2.retrieveAllEntityPaths().flowPath.path.forward

        when: "Check potential path that has NO intersection with both flows from diverse group"
        Path pathToCheck = switchPairs.all().neighbouring().excludePairs([firstSwitchPair, secondSwitchPair])
                .includeSwitch(firstSwitchPair.src).random().retrieveAvailablePaths().first()

        if(pathToCheck.retrieveNodes().last().switchId != firstSwitchPair.src.dpId) {
            //it is required for the further correct path view building
            pathToCheck.nodes.setNodes(pathToCheck.nodes.nodes.reverse())
        }

        then: "Path check reports has No validation error about intersection"
        verifyAll {
            assert northboundV2.checkPath(generatePathPayload(pathToCheck, flow1.flowId)).getValidationMessages().isEmpty()
            assert northboundV2.checkPath(generatePathPayload(pathToCheck, flow2.flowId)).getValidationMessages().isEmpty()
        }

        when: "Check potential path that has intersection ONLY with one flow from diverse group"
        pathToCheck = mergePaths(pathToCheck, flow1Path)
        def checkErrors = northboundV2.checkPath(generatePathPayload(pathToCheck, flow1.flowId))

        then: "Path check reports has ONLY one intersecting segment"
        verifyAll {
            assert checkErrors.getValidationMessages().size() == 1
            assert checkErrors.getValidationMessages().find { it.contains("The following segment intersects with the flow ${flow1.flowId}") }
        }

        when: "Check potential path that has intersection with both flows from diverse group"
        pathToCheck = mergePaths(pathToCheck, flow2Path)
        checkErrors = northboundV2.checkPath(generatePathPayload(pathToCheck, flow1.flowId))

        then: "Path check reports has intersecting segments with both flows from diverse group"
        verifyAll {
            assert checkErrors.getValidationMessages().size() == 2
            assert checkErrors.getValidationMessages().find { it.contains("The following segment intersects with the flow ${flow1.flowId}") }
            assert checkErrors.getValidationMessages().find { it.contains("The following segment intersects with the flow ${flow2.flowId}") }
        }
    }

    private static org.openkilda.messaging.payload.flow.FlowEncapsulationType convertEncapsulationType(FlowEncapsulationType origin) {
        // Let's laugh on this naive implementation after the third encapsulation type is introduced, not before.
        if (origin == VXLAN) {
            return org.openkilda.messaging.payload.flow.FlowEncapsulationType.VXLAN
        } else {
            return org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN
        }
    }

    private PathValidationPayload generatePathPayload(Path path,
                                                      String diverseWithFlow,
                                                      org.openkilda.model.PathComputationStrategy pathComputationStrategy = COST,
                                                      FlowEncapsulationType flowEncapsulationType = TRANSIT_VLAN) {
        PathValidationPayload.builder()
                .nodes(path.nodes.toPathNodePayload())
                .diverseWithFlow(diverseWithFlow)
                .pathComputationStrategy(pathComputationStrategy)
                .flowEncapsulationType(convertEncapsulationType(flowEncapsulationType))
                .build()
    }

    private Path mergePaths(Path pathToCheck, Path flowPathToAppend) {
        LinkedList<PathNodeV2> mergedNodes = pathToCheck.retrieveNodes()
        //remove first and last elements (not used in Path view)
        mergedNodes.addAll(flowPathToAppend.nodes.toPathNodeV2())
        pathToCheck.nodes.setNodes(mergedNodes)
        return pathToCheck
    }
}
