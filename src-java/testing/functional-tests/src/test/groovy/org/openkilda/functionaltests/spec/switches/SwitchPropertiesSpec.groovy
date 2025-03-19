package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.VXLAN
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.CLASS
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.switchproperties.SwitchPropertiesNotFoundExpectedError
import org.openkilda.functionaltests.error.switchproperties.SwitchPropertiesNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto

import groovy.transform.AutoClone
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import java.util.regex.Pattern

@Narrative("""Switch properties are created automatically once switch is connected to the controller
and deleted once switch is deleted.
Properties can be read/updated via API '/api/v1/switches/:switch-id/properties'.
Main purpose of that is to understand which feature is supported by a switch(encapsulation type, multi table)""")

class SwitchPropertiesSpec extends HealthCheckSpecification {

    @Shared
    SwitchExtended switchUnderTest

    @Autowired @Shared
    CleanupManager cleanupManager

    def setupSpec() {
        switchUnderTest = switches.all().first()
        def initialProps = switchUnderTest.getCachedProps()
        cleanupManager.addAction(OTHER, { northbound.updateSwitchProperties(switchUnderTest.switchId, initialProps) }, CLASS)
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to manipulate with switch properties"() {
        given: "A switch that supports VXLAN"
        def sw = switches.all().findWithVxlanFeatureEnabled()
        def initSwitchProperties = sw.getProps()
        assert !initSwitchProperties.supportedTransitEncapsulation.empty
        //make sure that two endpoints have the same info
        assert sw.getPropsV1().supportedTransitEncapsulation.sort() == initSwitchProperties.supportedTransitEncapsulation.sort()

        when: "Update switch properties"
        def newTransitEncapsulation = (initSwitchProperties.supportedTransitEncapsulation.size() == 1) ?
                [TRANSIT_VLAN.toString(), VXLAN.toString()].sort() : [VXLAN.toString()]

        SwitchPropertiesDto switchProperties = initSwitchProperties.jacksonCopy().tap {
            supportedTransitEncapsulation = newTransitEncapsulation
            multiTable = true
        }
        def updateSwPropertiesResponse = sw.updateProperties(switchProperties)

        then: "Correct response is returned"
        updateSwPropertiesResponse.supportedTransitEncapsulation.sort() == newTransitEncapsulation

        and: "Switch properties is really updated"
        sw.getPropsV1().supportedTransitEncapsulation.sort() == newTransitEncapsulation

        and: "Changes are shown in getAllSwitchProperties response"
        sw.getProps().supportedTransitEncapsulation.sort() == newTransitEncapsulation

    }

    def "Informative error is returned when trying to get/update switch properties with non-existing id"() {
        when: "Try to get switch properties info for non-existing switch"
        northbound.getSwitchProperties(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchPropertiesNotFoundExpectedError(NON_EXISTENT_SWITCH_ID, ~/Failed to get switch properties./).matches(e)
        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.tap {
            supportedTransitEncapsulation = [VXLAN.toString()]
            multiTable = true
        }
        northbound.updateSwitchProperties(NON_EXISTENT_SWITCH_ID, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new SwitchPropertiesNotFoundExpectedError(NON_EXISTENT_SWITCH_ID, ~/Failed to update switch properties./).matches(exc)    }

    def "Informative error is returned when trying to update switch properties with incorrect information(#invalidType)"() {
        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.supportedTransitEncapsulation = supportedTransitEncapsulation
        northbound.updateSwitchProperties(switchUnderTest.switchId, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        expectedError.matches(exc)

        where:
        invalidType    | supportedTransitEncapsulation | expectedError
        "invalid type" | ["test"]                      | new SwitchPropertiesNotUpdatedExpectedError("Unable to parse request payload",
                                                        ~/No enum constant org.openkilda.messaging.payload.flow.FlowEncapsulationType.TEST/)
        "empty list"   | []                            | new SwitchPropertiesNotUpdatedExpectedError(
                                                         "Supported transit encapsulations should not be null or empty")
        "null"         | null                          | new SwitchPropertiesNotUpdatedExpectedError(
                                                         "Supported transit encapsulations should not be null or empty")
    }

    def "Error is returned when trying to #data.desc"() {
        when: "Try to update switch properties with incorrect server 42 properties combination"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.supportedTransitEncapsulation = [TRANSIT_VLAN.toString()]
        switchProperties.multiTable = true
        switchProperties.server42FlowRtt = data.server42FlowRtt
        switchProperties.server42Port = data.server42Port
        switchProperties.server42Vlan = data.server42Vlan
        switchProperties.server42MacAddress = data.server42MacAddress
        switchProperties.server42IslRtt = data.server42IslRtt
        northbound.updateSwitchProperties(switchUnderTest.switchId, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new SwitchPropertiesNotUpdatedExpectedError(String.format(data.error, switchUnderTest.switchId),
                data.description ?: ~/Failed to update switch properties./).matches(exc)

        where:
        data << [
                new PropertiesData(desc: "enable server42_flow_rtt property without server42_port property",
                        server42FlowRtt: true, server42Port: null, server42MacAddress: "42:42:42:42:42:42",
                        server42Vlan: 15,
                        error: "Illegal switch properties combination for switch %s. To enable property " +
                                "'server42_flow_rtt' you need to specify valid property 'server42_port'"),

                new PropertiesData(desc: "enable server42_flow_rtt property without server42_mac_address property",
                        server42FlowRtt: true, server42Port: 42, server42MacAddress: null,
                        server42Vlan: 15,
                        error: "Illegal switch properties combination for switch %s. To enable property " +
                                "'server42_flow_rtt' you need to specify valid property 'server42_mac_address'"),

                new PropertiesData(desc: "enable server42_flow_rtt property without server42_vlan property",
                        server42FlowRtt: true, server42Port: 42, server42MacAddress: "42:42:42:42:42:42",
                        server42Vlan: null,
                        error: "Illegal switch properties combination for switch %s. To enable property " +
                                "'server42_flow_rtt' you need to specify valid property 'server42_vlan'"),

                new PropertiesData(desc: "set invalid server42_port property",
                        server42FlowRtt: true, server42Port: -1, server42MacAddress: null,
                        server42Vlan: 15,
                        error: "Property 'server42_port' for switch %s has invalid value '-1'. Port must be positive",
                        description: ~/Invalid server 42 Port/),

                new PropertiesData(desc: "set invalid server42mac_address property",
                        server42FlowRtt: false, server42Port: null, server42MacAddress: "INVALID",
                        server42Vlan: 15,
                        error: "Property 'server42_mac_address' for switch %s has invalid value 'INVALID'.",
                        description: ~/Invalid server 42 Mac Address/),

                new PropertiesData(desc: "set invalid server42_vlan property",
                        server42FlowRtt: false, server42Port: null, server42MacAddress: null,
                        server42Vlan: -1,
                        error: "Property 'server42_vlan' for switch %s has invalid value '-1'. Vlan must be in range [0, 4095]",
                        description: ~/Invalid server 42 Vlan/),

                new PropertiesData(desc: "enable server42_isl_rtt property without server42_port property",
                        server42IslRtt: "ENABLED", server42Port: null, server42MacAddress: "42:42:42:42:42:42",
                        error: "Illegal switch properties combination for switch %s. To enable property " +
                                "'server42_isl_rtt' you need to specify valid property 'server42_port'"),

                new PropertiesData(desc: "enable server42_isl_rtt property without server42_mac_address property",
                        server42IslRtt: "ENABLED", server42Port: 42, server42MacAddress: null,
                        error: "Illegal switch properties combination for switch %s. To enable property " +
                                "'server42_isl_rtt' you need to specify valid property 'server42_mac_address'")
        ]
    }

    @AutoClone
    private static class PropertiesData {
        boolean server42FlowRtt
        Integer server42Port, server42Vlan
        String server42MacAddress, desc, error
        String server42IslRtt
        Pattern description = null
    }
}
