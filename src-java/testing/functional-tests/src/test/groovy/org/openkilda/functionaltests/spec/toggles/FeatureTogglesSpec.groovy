package org.openkilda.functionaltests.spec.toggles

import org.openkilda.functionaltests.error.flow.FlowForbiddenToCreateExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToDeleteExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToUpdateExpectedError

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.model.system.FeatureTogglesDto

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative

@Narrative("""
Feature Toggles is a special lever that allows to turn on/off certain Kilda features. For example, we can disable
creation of new flows via Northbound API. This spec verifies that Feature Toggle restrictions are applied correctly.
""")
/*Note that the 'flowReroute' toggle is tested under AutoRerouteSpec#"Flow goes to 'Down' status when an intermediate
switch is disconnected and there is no ability to reroute".
BFD toggle is tested in BfdSpec*/
@Tags([SMOKE, LOW_PRIORITY])
@Isolated
class FeatureTogglesSpec extends HealthCheckSpecification {

    @Tidy
    def "System forbids creating new flows when 'create_flow' toggle is set to false"() {
        given: "Existing flow"
        def flowRequest = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        def flow = flowHelper.addFlow(flowRequest)

        when: "Set create_flow toggle to false"
        def disableFlowCreation = northbound.toggleFeature(FeatureTogglesDto.builder().createFlowEnabled(false).build())

        and: "Try to create a new flow"
        northbound.addFlow(flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToCreateExpectedError(~/Flow create feature is disabled/).matches(e)
        and: "Update of previously existing flow is still possible"
        flowHelper.updateFlow(flow.id, flowRequest.tap { it.description = it.description + "updated" })

        and: "Delete of previously existing flow is still possible"
        def deletedFlow = flowHelper.deleteFlow(flow.id)

        cleanup: "set create_flow toggle back to true and delete resources if required"
        disableFlowCreation && northbound.toggleFeature(FeatureTogglesDto.builder().createFlowEnabled(true).build())
        flow && !deletedFlow && flowHelper.deleteFlow(flow.id)
    }

    @Tidy
    def "System forbids updating flows when 'update_flow' toggle is set to false"() {
        given: "Existing flow"
        def flowRequest = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        def flow = flowHelper.addFlow(flowRequest)

        when: "Set update_flow toggle to false"
        def disableFlowUpdating = northbound.toggleFeature(FeatureTogglesDto.builder().updateFlowEnabled(false).build())

        and: "Try to update the flow"
        northbound.updateFlow(flowRequest.id, flowRequest.tap { it.description = it.description + "updated" })

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToUpdateExpectedError(~/Flow update feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        def newFlow = flowHelper.addFlow(flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        cleanup: "set update_flow toggle back to true and delete created link"
        flow && flowHelper.deleteFlow(flow.id)
        newFlow && flowHelper.deleteFlow(newFlow.id)
        disableFlowUpdating && northbound.toggleFeature(FeatureTogglesDto.builder().updateFlowEnabled(true).build())
    }

    @Tidy
    def "System forbids deleting flows when 'delete_flow' toggle is set to false"() {
        given: "Existing flow"
        def flowRequest = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        def flow = flowHelper.addFlow(flowRequest)

        when: "Set delete_flow toggle to false"
        def disableFlowDeletion = northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(false).build())

        and: "Try to delete the flow"
        northbound.deleteFlow(flowRequest.id)

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToDeleteExpectedError(~/Flow delete feature is disabled/).matches(e)
        and: "Creating new flow is still possible"
        def newFlow = flowHelper.addFlow(flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        and: "Updating of flow is still possible"
        flowHelper.updateFlow(flowRequest.id, flowRequest.tap { it.description = it.description + "updated" })

        when: "Set delete_flow toggle back to true"
        def enableFlowDeletion = northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(true).build())

        then: "Able to delete flows"
        def deletedFlow = flowHelper.deleteFlow(flow.id)
        def deletedNewFlow = flowHelper.deleteFlow(newFlow.id)

        cleanup:
        disableFlowDeletion && !enableFlowDeletion && northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(true).build())
        flow && !deletedFlow && flowHelper.deleteFlow(flow.id)
        newFlow && !deletedNewFlow && flowHelper.deleteFlow(newFlow.id)
    }
}
