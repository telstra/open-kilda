package org.openkilda.functionaltests.spec.toggles

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.system.FeatureTogglesDto

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Narrative

@Narrative("""
Feature Toggles is a special lever that allows to turn on/off certain Kilda features. For example, we can disable
creation of new flows via Northbound API. This spec verifies that Feature Toggle restrictions are applied correctly.
""")
/*Note that the 'flowReroute' toggle is tested under AutoRerouteSpec#"Flow goes to 'Down' status when an intermediate
switch is disconnected and there is no ability to reroute"*/
class FeatureTogglesSpec extends BaseSpecification {
    def "System forbids creating new flows when 'create_flow' toggle is set to false"() {
        given: "Existing flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        when: "Set create_flow toggle to false"
        northbound.toggleFeature(FeatureTogglesDto.builder().createFlowEnabled(false).build())

        and: "Try to create a new flow"
        northbound.addFlow(flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpServerErrorException)
        //TODO(rtretiak): inappropriate status code. Issue #1920
        e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Feature toggles not enabled for CREATE_FLOW operation."

        and: "Update of previously existing flow is still possible"
        flowHelper.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        and: "Delete of previously existing flow is still possible"
        flowHelper.deleteFlow(flow.id)

        and: "Cleanup: set create_flow toggle back to true"
        northbound.toggleFeature(FeatureTogglesDto.builder().createFlowEnabled(true).build())
    }

    def "System forbids updating flows when 'update_flow' toggle is set to false"() {
        given: "Existing flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        when: "Set update_flow toggle to false"
        northbound.toggleFeature(FeatureTogglesDto.builder().updateFlowEnabled(false).build())

        and: "Try to update the flow"
        northbound.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpServerErrorException)
        //TODO(rtretiak): inappropriate status code. Issue #1920
        e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Feature toggles not enabled for UPDATE_FLOW operation."

        and: "Creating new flow is still possible"
        def newFlow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(newFlow)

        and: "Deleting of flows is still possible"
        [newFlow, flow].each { flowHelper.deleteFlow(it.id) }

        and: "Cleanup: set update_flow toggle back to true"
        northbound.toggleFeature(FeatureTogglesDto.builder().updateFlowEnabled(true).build())
    }

    def "System forbids deleting flows when 'delete_flow' toggle is set to false"() {
        given: "Existing flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        when: "Set delete_flow toggle to false"
        northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(false).build())

        and: "Try to delete the flow"
        northbound.deleteFlow(flow.id)

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpServerErrorException)
        //TODO(rtretiak): inappropriate status code. Issue #1920
        e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "Can not delete flow: Feature toggles not enabled for DELETE_FLOW operation."

        and: "Creating new flow is still possible"
        def newFlow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(newFlow)

        and: "Updating of flow is still possible"
        flowHelper.updateFlow(flow.id, flow.tap { it.description = it.description + "updated" })

        when: "Set delete_flow toggle back to true"
        northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(true).build())

        then: "Able to delete flows"
        [flow, newFlow].each { flowHelper.deleteFlow(it.id) }
    }
}
