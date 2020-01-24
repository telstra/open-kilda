package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchFeature
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative

@Narrative("""Switch properties are created automatically once switch is connected to the controller
and deleted once switch is deleted.
Properties can be read/updated via API '/api/v1/switches/:switch-id/properties'.
Main purpose of that is to understand which feature is supported by a switch(encapsulation type, multi table)""")
class SwitchPropertiesSpec extends HealthCheckSpecification {

    @Ignore("https://github.com/telstra/open-kilda/issues/3059")
    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to manipulate with switch properties"() {
        given: "A switch that supports VXLAN"
        def sw = topology.activeSwitches.find { it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }
        assumeTrue("Wasn't able to find vxlan-enabled switch", sw as boolean)
        def initSwitchProperties = northbound.getSwitchProperties(sw.dpId)
        assert initSwitchProperties.multiTable != null
        assert !initSwitchProperties.supportedTransitEncapsulation.empty

        when: "Update switch properties"
        SwitchPropertiesDto switchProperties = new SwitchPropertiesDto()
        def newMultiTable = !initSwitchProperties.multiTable
        def newTransitEncapsulation = (initSwitchProperties.supportedTransitEncapsulation.size() == 1) ?
                [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase(),
                 FlowEncapsulationType.VXLAN.toString().toLowerCase()].sort() :
                [FlowEncapsulationType.VXLAN.toString().toLowerCase()]
        switchProperties.multiTable = newMultiTable
        switchProperties.supportedTransitEncapsulation = newTransitEncapsulation
        def updateSwPropertiesResponse = SwitchHelper.updateSwitchProperties(sw, switchProperties)

        then: "Correct response is returned"
        updateSwPropertiesResponse.multiTable == newMultiTable
        updateSwPropertiesResponse.supportedTransitEncapsulation.sort() == newTransitEncapsulation

        and: "Switch properties is really updated"
        with(northbound.getSwitchProperties(sw.dpId)) {
            it.multiTable == newMultiTable
            it.supportedTransitEncapsulation.sort() == newTransitEncapsulation
        }

        cleanup: "Restore init switch properties on the switch"
        SwitchHelper.updateSwitchProperties(sw, initSwitchProperties)
    }

    def "Informative error is returned when trying to get/update switch properties with non-existing id"() {
        when: "Try to get switch properties info for non-existing switch"
        northbound.getSwitchProperties(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "Switch properties for switch id '$NON_EXISTENT_SWITCH_ID' not found."

        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.multiTable = true
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.VXLAN.toString()]
        northbound.updateSwitchProperties(NON_EXISTENT_SWITCH_ID, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Switch properties for switch id '$NON_EXISTENT_SWITCH_ID' not found."
    }

    def "Informative error is returned when trying to update switch properties with incorrect information"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.supportedTransitEncapsulation = supportedTransitEncapsulation
        northbound.updateSwitchProperties(sw.dpId, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == errorMessage

        where:
        supportedTransitEncapsulation | errorMessage
        ["test"]                      | "Unable to parse request payload"
        []                            | "Supported transit encapsulations should not be null or empty"
        null                          | "Supported transit encapsulations should not be null or empty"
    }

    def "Unable to turn on switchLldp property without turning on multiTable property"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Try to update set switchLldp property to True and multiTable property to False"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.VXLAN.toString()]
        switchProperties.multiTable = false
        switchProperties.switchLldp = true
        northbound.updateSwitchProperties(sw.dpId, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage.contains(
                "Illegal switch properties combination for switch $sw.dpId.")
    }

    @Tidy
    def "System forbids to turn on VXLAN encap type on switch that does not support it"() {
        given: "Switch that does not support VXLAN feature"
        def sw = topology.activeSwitches.find { !it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }
        assumeTrue("There is no non-vxlan switch in the topology", sw as boolean)

        when: "Try to turn on VXLAN encap type on that switch"
        def initProps = northbound.getSwitchProperties(sw.dpId)
        northbound.updateSwitchProperties(sw.dpId, initProps.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [FlowEncapsulationType.VXLAN.toString()]
        })

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        e.responseBodyAsString.to(MessageError).errorDescription ==
                "Switch $sw.dpId doesn't support requested feature NOVIFLOW_COPY_FIELD"

        cleanup:
        !e && SwitchHelper.updateSwitchProperties(sw, initProps)
    }
}
