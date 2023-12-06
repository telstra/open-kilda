package org.openkilda.functionaltests.spec.flows.haflows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload

import com.shazam.shazamcrest.matcher.CustomisableMatcher
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify operations with protected paths on HA-flows.")
class HaFlowProtectedSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    def "Able to enable protected path on an HA-flow"() {
        given: "A simple HA-flow"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        HaFlow haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def haFlowPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        assert !haFlowPaths.sharedPath.protectedPath
        def switchesBeforeUpdate = haFlowHelper.getInvolvedSwitches(haFlowPaths)

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def update = haFlowHelper.convertToUpdate(haFlow.tap { it.allocateProtectedPath = true })
        def updateResponse = haFlowHelper.updateHaFlow(haFlow.haFlowId, update)

        then: "Update response contains enabled protected path"
        updateResponse.allocateProtectedPath

        and: "Protected path is really enabled on the HA-Flow"
        northboundV2.getHaFlow(haFlow.haFlowId).allocateProtectedPath

        and: "Protected path is really created"
        def paths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        paths.subFlowPaths.each {
            assert it.protectedPath
            assert it.forward != it.protectedPath.forward
        }
        def switchesAfterUpdate = haFlowHelper.getInvolvedSwitches(paths)

        // not implemented yet https://github.com/telstra/open-kilda/issues/5152
        // and: "HA-Flow and related sub-flows are valid"
        // northboundV2.validateHaFlow(haFlow.haFlowId).asExpected

        and: "All involved switches passes switch validation"
        withPool {
            (switchesBeforeUpdate + switchesAfterUpdate).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        and: "HA-flow pass validation"
        northboundV2.validateHaFlow(haFlow.getHaFlowId()).asExpected

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    def "Able to disable protected path on an HA-flow via partial update"() {
        given: "An HA-flow with protected path"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        haFlowRequest.allocateProtectedPath = true
        HaFlow haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def haFlowPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        assert haFlowPaths.sharedPath.protectedPath
        def switchesBeforeUpdate = haFlowHelper.getInvolvedSwitches(haFlowPaths)

        when: "Patch flow: disable protected path(allocateProtectedPath=false)"
        def patch = HaFlowPatchPayload.builder().allocateProtectedPath(false).build()
        def patchResponse = haFlowHelper.partialUpdateHaFlow(haFlow.haFlowId, patch)

        then: "Patch response contains disabled protected path"
        !patchResponse.allocateProtectedPath

        and: "Protected path is really disabled on the HA-Flow"
        !northboundV2.getHaFlow(haFlow.haFlowId).allocateProtectedPath

        and: "Protected path is really removed"
        def paths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        paths.subFlowPaths.each {
            assert !it.protectedPath
        }
        def switchesAfterUpdate = haFlowHelper.getInvolvedSwitches(paths)

        // not implemented yet https://github.com/telstra/open-kilda/issues/5152
        // and: "HA-Flow and related sub-flows are valid"
        // northboundV2.validateHaFlow(haFlow.haFlowId).asExpected

        and: "All involved switches passes switch validation"
        withPool {
            (switchesBeforeUpdate + switchesAfterUpdate).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        and: "HA-flow pass validation"
        northboundV2.validateHaFlow(haFlow.getHaFlowId()).asExpected

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    def "User can update #data.descr of a ha-flow with protected path"() {
        given: "An HA-flow with protected path"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        haFlowRequest.allocateProtectedPath = true
        HaFlow haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def haFlowPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        assert haFlowPaths.sharedPath.protectedPath
        haFlow.tap(data.updateClosure)
        def update = haFlowHelper.convertToUpdate(haFlow)

        when: "Update the ha-flow"
        def updateResponse = haFlowHelper.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate",
                       "subFlows.status",
                       "subFlows.forwardLatency",
                       "subFlows.reverseLatency",
                       "subFlows.latencyLastModifiedTime",
                       "timeUpdate",
                       "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlow, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlow, ignores)

        and: "And involved switches pass validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(haFlow.haFlowId).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        and: "HA-flow pass validation"
        northboundV2.validateHaFlow(haFlow.getHaFlowId()).asExpected

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { HaFlow payload ->
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
                        updateClosure: { HaFlow payload ->
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
