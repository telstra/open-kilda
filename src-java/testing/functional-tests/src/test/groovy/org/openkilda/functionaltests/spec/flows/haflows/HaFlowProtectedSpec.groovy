package org.openkilda.functionaltests.spec.flows.haflows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload

import com.shazam.shazamcrest.matcher.CustomisableMatcher
import groovy.util.logging.Slf4j
import spock.lang.Narrative

@Slf4j
@Narrative("Verify operations with protected paths on Ha-Flows.")
@Tags([HA_FLOW])
class HaFlowProtectedSpec extends HealthCheckSpecification {

    def "Able to enable protected path on an HA-Flow"() {
        given: "A simple HA-Flow"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()
        assert !haFlow.allocateProtectedPath

        def haFlowPaths = haFlow.retrievedAllEntityPaths()
        assert haFlowPaths.subFlowPaths.protectedPath.forward.isEmpty()
        def switchesBeforeUpdate = haFlowPaths.getInvolvedSwitches(true)

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def updateRequest = haFlow.convertToUpdateRequest().tap { allocateProtectedPath = true}
        def updateResponse = haFlow.update(updateRequest)

        then: "Update response contains enabled protected path"
        updateResponse.allocateProtectedPath

        and: "Protected path is really enabled on the HA-Flow"
        haFlow.retrieveDetails().allocateProtectedPath

        and: "Protected path is really created"
        def pathsAfterEnablingProtected = haFlow.retrievedAllEntityPaths()
        pathsAfterEnablingProtected.subFlowPaths.each {subFlowPath ->
            assert subFlowPath.protectedPath
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        and: "HA-Flow pass validation"
        def haFlowValidation = haFlow.validate()
        haFlowValidation.asExpected
        haFlowValidation.getSubFlowValidationResults().each { assert it.getDiscrepancies().isEmpty() }
        //for both path and protected path in both forward and reverse directions
        haFlowValidation.getSubFlowValidationResults().size() == 4

        and: "All involved switches passes switch validation"
        def switchesAfterUpdate = pathsAfterEnablingProtected.getInvolvedSwitches(true)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switchesBeforeUpdate + switchesAfterUpdate).isEmpty()

        cleanup:
        haFlow && haFlow.delete()
    }

    def "Able to disable protected path on an HA-Flow via partial update"() {
        given: "An HA-Flow with protected path"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).withProtectedPath(true).create()

        def haFlowPaths = haFlow.retrievedAllEntityPaths()
        assert !haFlowPaths.subFlowPaths.protectedPath.forward.isEmpty()
        def switchesBeforeUpdate = haFlowPaths.getInvolvedSwitches(true)

        when: "Patch flow: disable protected path(allocateProtectedPath=false)"
        def updateResponse = haFlow.partialUpdate(HaFlowPatchPayload.builder().allocateProtectedPath(false).build())

        then: "Patch response contains disabled protected path"
        !updateResponse.allocateProtectedPath

        and: "Protected path is really disabled on the HA-Flow"
        !haFlow.retrieveDetails().allocateProtectedPath

        and: "Protected path is really removed"
        def pathsAfterUpdate = haFlow.retrievedAllEntityPaths()
        pathsAfterUpdate.subFlowPaths.protectedPath.forward.isEmpty()
        and: "HA-Flow pass validation"
        def haFlowValidation = haFlow.validate()
        haFlowValidation.asExpected
        haFlowValidation.getSubFlowValidationResults().each { assert it.getDiscrepancies().isEmpty() }
        //only for path in both forward and reverse directions
        haFlowValidation.getSubFlowValidationResults().size() == 2

        and: "All involved switches passes switch validation"
        def switchesAfterUpdate = pathsAfterUpdate.getInvolvedSwitches(true)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switchesBeforeUpdate + switchesAfterUpdate).isEmpty()


        cleanup:
        haFlow && haFlow.delete()
    }

    def "User can update #data.descr of a HA-Flow with protected path"() {
        given: "An HA-Flow with protected path"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")

        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).withProtectedPath(true).create()
        def haFlowPaths = haFlow.retrievedAllEntityPaths()
        assert !haFlowPaths.subFlowPaths.protectedPath.forward.isEmpty()

        def haFlowDetails = haFlow.waitForBeingInState(FlowState.UP)
        assert haFlowDetails.allocateProtectedPath

        haFlow.tap(data.updateClosure)

        def update = haFlow.convertToUpdateRequest()

        when: "Update the ha-Flow"
        def updateResponse = northboundV2.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate",
                       "subFlows.status",
                       "subFlows.forwardLatency",
                       "subFlows.reverseLatency",
                       "subFlows.latencyLastModifiedTime",
                       "timeUpdate",
                       "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        haFlowDetails.tap(data.updateClosure)
        expect updateResponse, sameBeanAs(haFlowDetails, ignores)

        def haFlowDetailsAfterUpdating = haFlow.waitForBeingInState(FlowState.UP)
        expect haFlowDetailsAfterUpdating, sameBeanAs(updateResponse, ignores)

        and: "And involved switches pass validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies( haFlow.retrievedAllEntityPaths().getInvolvedSwitches(true)).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup:
        haFlow && haFlow.delete()

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { def payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpoint.switchId)) - it.endpoint.portNumber
                                it.endpoint.portNumber = allowedPorts[0]
                            }
                        }
                ],
                [
                        descr: "shared switch and subflow switches",
                        updateClosure: { def payload ->
                            def newSwT = topologyHelper.getSwitchTriplets(true).find {
                                it.shared.dpId != payload.sharedEndpoint.switchId &&
                                        it.ep1.dpId != payload.subFlows[0].endpoint.switchId &&
                                        it.ep2.dpId != payload.subFlows[1].endpoint.switchId &&
                                        it.ep1 != it.ep2
                            }
                            payload.sharedEndpoint.switchId = newSwT.shared.dpId
                            payload.subFlows[0].endpoint.switchId = newSwT.ep1.dpId
                            payload.subFlows[1].endpoint.switchId = newSwT.ep2.dpId
                            payload.sharedEndpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.shared.dpId))[-1]
                            payload.subFlows[0].endpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            payload.subFlows[1].endpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep2.dpId))[-1]
                        }
                ],
                [
                        descr: "[without any changes in update request]",
                        updateClosure: { }
                ]
        ]
    }

    static <T> CustomisableMatcher<T> sameBeanAs(final T expected, List<String> ignores) {
        def matcher = sameBeanAs(expected)
        ignores.each { matcher.ignoring(it) }
        return matcher
    }
}
