package org.openkilda.functionaltests.spec.toggles

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowForbiddenToCreateExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToDeleteExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToUpdateExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.model.cleanup.CleanupManager

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_FEATURE_TOGGLE

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

    @Autowired
    @Shared
    FlowFactory flowFactory

    def "System forbids creating new flows when 'create_flow' toggle is set to false"() {
        given: "Existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandomV1(swPair)

        when: "Set create_flow toggle to false"
        featureToggles.createFlowEnabled(false)

        and: "Try to create a new flow"
        flowFactory.getBuilder(swPair).build().sendCreateRequestV1()

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToCreateExpectedError(~/Flow create feature is disabled/).matches(e)

        and: "Update of previously existing flow is still possible"
        flow.updateV1(flow.tap { it.description = it.description + "updated" })

        and: "Delete of previously existing flow is still possible"
        flow.deleteV1()
    }

    def "System forbids updating flows when 'update_flow' toggle is set to false"() {
        given: "Existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandomV1(swPair)

        when: "Set update_flow toggle to false"
        featureToggles.updateFlowEnabled(false)

        and: "Try to update the flow"
        flow.updateV1(flow.tap { it.description = it.description + "updated" })

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToUpdateExpectedError(~/Flow update feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        flowFactory.getRandomV1(swPair)
    }

    def "System forbids deleting flows when 'delete_flow' toggle is set to false"() {
        given: "Existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandomV1(swPair)

        when: "Set delete_flow toggle to false"
        featureToggles.deleteFlowEnabled(false)

        and: "Try to delete the flow"
        flow.sendDeleteRequestV1()

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToDeleteExpectedError(~/Flow delete feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        def newFlow = flowFactory.getRandomV1(swPair)

        and: "Updating of flow is still possible"
        flow.updateV1(flow.tap { it.description = it.description + "updated" })

        when: "Set delete_flow toggle back to true"
        featureToggles.deleteFlowEnabled(true)

        then: "Able to delete flows"
        flow.deleteV1()
        newFlow.deleteV1()
    }
}
