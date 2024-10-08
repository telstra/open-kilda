package org.openkilda.functionaltests.spec.configuration

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.NonExistingEncapsulationTypeExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.model.FlowEncapsulationType

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

@Narrative("""
Kilda configuration is a special lever that allows to change default flow encapsulation type while creating.
This spec assumes that 'transit_vlan' is always default type
""")
@Isolated //kilda config updates
class ConfigurationSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Shared
    FlowEncapsulationType defaultEncapsulationType = FlowEncapsulationType.TRANSIT_VLAN


    def "System takes into account default flow encapsulation type while creating a flow"() {
        when: "Create a flow without encapsulation type"
        def switchPair = switchPairs.all()
                .neighbouring()
                .withBothSwitchesVxLanEnabled()
                .random()
        def flow1 = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(null).build()
                .create()

        then: "Flow is created with current default encapsulation type(transit_vlan)"
        flow1.retrieveDetails().encapsulationType.toString() == defaultEncapsulationType.toString().toLowerCase()

        when: "Update default flow encapsulation type"
        def newFlowEncapsulationType = FlowEncapsulationType.VXLAN
        def updateResponse = kildaConfiguration.updateFlowEncapsulationType(newFlowEncapsulationType)

        then: "Correct response is returned"
        updateResponse.flowEncapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        and: "Kilda configuration is really updated"
        kildaConfiguration.getKildaConfiguration().flowEncapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        when: "Create a flow without encapsulation type"
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withEncapsulationType(null).build()
                .create()

        then: "Flow is created with new default encapsulation type(vxlan)"
        flow2.retrieveDetails().encapsulationType.toString() == newFlowEncapsulationType.toString().toLowerCase()
    }

    @Tags(LOW_PRIORITY)
    def "System doesn't allow to update kilda configuration with wrong flow encapsulation type"() {
        when: "Try to set wrong flow encapsulation type"
        def incorrectValue = "TEST"
        kildaConfiguration.updateFlowEncapsulationType(incorrectValue)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new NonExistingEncapsulationTypeExpectedError(incorrectValue).matches(e)
    }

}
