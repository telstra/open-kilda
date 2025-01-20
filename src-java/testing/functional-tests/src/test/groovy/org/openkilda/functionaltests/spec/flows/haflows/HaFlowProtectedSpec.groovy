package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW

@Slf4j
@Narrative("Verify operations with protected paths on Ha-Flows.")
@Tags([HA_FLOW])
class HaFlowProtectedSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    def "Able to enable protected path on an HA-Flow"() {
        given: "A simple HA-Flow"
        def swT = switchTriplets.all().findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowFactory.getRandom(swT)
        assert !haFlow.allocateProtectedPath

        def haFlowPaths = haFlow.retrievedAllEntityPaths()
        assert haFlowPaths.subFlowPaths.protectedPath.forward.isEmpty()

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
        def involvedSwitches = switches.all().findSpecific(
                [haFlowPaths, pathsAfterEnablingProtected].collectMany { it.getInvolvedSwitches() })
        synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()
    }

    def "Able to disable protected path on an HA-Flow via partial update"() {
        given: "An HA-Flow with protected path"
        def swT = switchTriplets.all().findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(true)
                .build().create()

        def haFlowPaths = haFlow.retrievedAllEntityPaths()
        assert !haFlowPaths.subFlowPaths.protectedPath.forward.isEmpty()

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
        def involvedSwitches = switches.all().findSpecific(
                [haFlowPaths, pathsAfterUpdate].collectMany { it.getInvolvedSwitches() })
        synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()
    }

    def "User can update #data.descr of an HA-Flow with protected path"() {
        given: "An HA-Flow with protected path"
        def swT = switchTriplets.all().findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")

        def haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(true)
                .build().create()
        assert haFlow.allocateProtectedPath

        def haFlowPaths = haFlow.retrievedAllEntityPaths()
        assert !haFlowPaths.subFlowPaths.protectedPath.forward.isEmpty()

        haFlow.tap(data.updateClosure)
        def update = haFlow.convertToUpdateRequest()

        when: "Update the ha-Flow"
        def updateResponse = haFlow.sendUpdateRequest(update)
        def updatedHaFlow = haFlow.waitForBeingInState(FlowState.UP)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(haFlow)
        updatedHaFlow.hasTheSamePropertiesAs(haFlow)

        and: "And involved switches pass validation"
        def involvedSwitches = switches.all().findSwitchesInPath(haFlow.retrievedAllEntityPaths())
        synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { HaFlowExtended payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpointSwitchId)) - it.endpointPort
                                it.endpointPort = allowedPorts[0]
                            }
                        }
                ],
                [
                        descr: "shared switch and subflow switches",
                        updateClosure: { HaFlowExtended payload ->
                            def newSwT = switchTriplets.all(true).getSwitchTriplets().find {
                                it.shared.switchId != payload.sharedEndpoint.switchId &&
                                        it.ep1.switchId != payload.subFlows[0].endpointSwitchId &&
                                        it.ep2.switchId != payload.subFlows[1].endpointSwitchId &&
                                        it.ep1 != it.ep2
                            }
                            payload.sharedEndpoint.switchId = newSwT.shared.switchId
                            payload.subFlows[0].endpointSwitchId = newSwT.ep1.switchId
                            payload.subFlows[1].endpointSwitchId = newSwT.ep2.switchId
                            payload.sharedEndpoint.portNumber = newSwT.shared.getPorts()[-1]
                            payload.subFlows[0].endpointPort = newSwT.ep1.getPorts()[-1]
                            payload.subFlows[1].endpointPort = newSwT.ep2.getPorts()[-1]
                        }
                ],
                [
                        descr: "[without any changes in update request]",
                        updateClosure: { }
                ]
        ]
    }
}
