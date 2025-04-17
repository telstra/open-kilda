package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.model.PortProperties.DISCOVERY_ENABLED_DEFAULT
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.PortNotFoundExpectedError
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/network-discovery/disable-port-discovery")
@Narrative("""Some switch ports should not be used in network discovery process.
By default all ports on all switches are available for discovery.
Admin has ability to enable/disable discovery on a specific port on a switch using Northbound REST API.

This spec assumes that port discovery property is enabled for all available ports.
""")
class PortPropertiesSpec extends HealthCheckSpecification {
    @Tags([SMOKE, SMOKE_SWITCHES])
    def "Able to manipulate port properties"() {
        given: "A port with port properties"
        // can't use `getAllowedPortsForSwitch` for virtual env in this test,
        // portProperties validate port number(port number should be in list of '/api/v1/switches/:switch-id/ports')
        def isl = isls.allNotConnected().first()
        with(isl.srcEndpoint.getNbProps()) {
            it.switchId == isl.srcSwId
            it.portNumber == isl.srcPort
            it.discoveryEnabled == DISCOVERY_ENABLED_DEFAULT
        }

        when: "Update port discovery property"
        PortPropertiesDto newPortProperties = new PortPropertiesDto()
        def newDiscoveryEnabled = !DISCOVERY_ENABLED_DEFAULT
        newPortProperties.discoveryEnabled = newDiscoveryEnabled
        def updatePortPropertiesResponse = isl.srcEndpoint.setDiscovery(false)

        then: "Correct response is returned"
        verifyAll(updatePortPropertiesResponse) {
            it.switchId == isl.srcSwId
            it.portNumber == isl.srcPort
            it.discoveryEnabled == newDiscoveryEnabled
        }

        and: "Port discovery property is really updated"
        Wrappers.wait(WAIT_OFFSET / 2) {
            with(isl.srcEndpoint.getNbProps()) {
                it.switchId == isl.srcSwId
                it.portNumber == isl.srcPort
                it.discoveryEnabled == newDiscoveryEnabled
            }
        }
    }

    def "Informative error is returned when trying to get/update port properties with non-existing switch"() {
        when: "Try to get port properties info for non-existing switch"
        //assume port 10 is always exist on a switch
        def port = 10
        northboundV2.getPortProperties(NON_EXISTENT_SWITCH_ID, port)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError(NON_EXISTENT_SWITCH_ID, ~/Couldn't get port properties/).matches(e)

        when: "Try to update port discovery property for non-existing switch"
        northboundV2.updatePortProperties(NON_EXISTENT_SWITCH_ID, port, new PortPropertiesDto(discoveryEnabled: true))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError("Could not update port properties for \
'${NON_EXISTENT_SWITCH_ID}_${port}': Switch ${NON_EXISTENT_SWITCH_ID} not found.", ~/Persistence exception/).matches(exc)    }

    def "Informative error is returned when trying to update port properties with non-existing port number"() {
        when: "Try to get port properties info for non-existing port"
        // Actually we have strange behaviour here, we can get port property for a non-existent port, but can't update
        def sw = switches.all().first()
        def nonExistentPort = sw.getPort(99999)
        def response = nonExistentPort.getNbProps()

        then: "No error, default port properties information is returned"
        with(response) {
            it.switchId == sw.switchId
            it.portNumber == nonExistentPort.port
            it.discoveryEnabled == DISCOVERY_ENABLED_DEFAULT
        }

        when: "Try to update port discovery property for non-existing port"
        nonExistentPort.setDiscovery(true)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new PortNotFoundExpectedError(sw.switchId, nonExistentPort.port, ~/Port not found exception/).matches(exc)    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System doesn't discover link when port discovery property is disabled"() {
        given: "A deleted link"
        def srcSw = switches.all().first()
        def islToManipulate = isls.all().relatedTo(srcSw).first()
        def isRtl = switches.all().findSpecific(islToManipulate.involvedSwIds).any { it.isRtlSupported() }

        // Bring port down on the src switch
        islToManipulate.breakIt()

        // delete link
        islToManipulate.delete()
        assert !islToManipulate.isPresent()

        when: "Disable port discovery property on the src and dst switches"
        islToManipulate.srcEndpoint.setDiscovery(false)
        islToManipulate.dstEndpoint.setDiscovery(false)

        and: "Bring port up on the src switch"
        islToManipulate.srcEndpoint.up()

        then: "Link is not detected"
        Wrappers.timedLoop(discoveryInterval + WAIT_OFFSET / 2) {
            assert !islToManipulate.isPresent()
        }

        when: "Deactivate/activate src switch"
        def blockData = srcSw.knockout(RW)
        srcSw.revive(blockData)

        then: "Link is still not detected"
        Wrappers.timedLoop(discoveryInterval) {
            assert !islToManipulate.isPresent()
        }

        when: "Enable port discovery property on the src switch"
        islToManipulate.srcEndpoint.setDiscovery(true)

        then: "Link is detected and status of REVERSE ISL is FAILED, FORWARD ISL is DISCOVERED"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            def islInfoForward = islToManipulate.getInfo(allLinks, false)
            assert islInfoForward.state == (isRtl ? DISCOVERED : FAILED)
            assert islInfoForward.actualState == DISCOVERED

            def islInfoReverse = islToManipulate.getInfo(allLinks, true)
            assert islInfoReverse.state == (isRtl ? DISCOVERED : FAILED)
            assert islInfoReverse.actualState == FAILED
        }

        when: "Enable port discovery property on the dst switch"
        islToManipulate.dstEndpoint.setDiscovery(true)

        then: "Link status is changed to DISCOVERED"
        islToManipulate.waitForStatus(DISCOVERED, WAIT_OFFSET)
    }

    @Tags([SMOKE, SMOKE_SWITCHES])
    def "Link is stopped from being discovered after disabling port discovery property"() {
        given: "An active link"
        def islToManipulate = isls.all().first()
        def isRtl = switches.all().findSpecific(islToManipulate.involvedSwIds).any { it.isRtlSupported() }

        when: "Disable port discovery property on the dst switch"
        islToManipulate.dstEndpoint.setDiscovery(false)

        then: "REVERSED ISL status is changed to FAILED, FORWARD ISL is DISCOVERED"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            def islInfoForward = islToManipulate.getInfo(allLinks, false)
            assert islInfoForward.state == (isRtl ? DISCOVERED : FAILED)
            assert islInfoForward.actualState == DISCOVERED

            def islInfoReverse = islToManipulate.getInfo(allLinks, true)
            assert islInfoReverse.state == (isRtl ? DISCOVERED : FAILED)
            assert islInfoReverse.actualState == FAILED
        }

        when: "Enable port discovery property on the dst switch"
        islToManipulate.dstEndpoint.setDiscovery(true)

        then: "Link state is changed to DISCOVERED"
        islToManipulate.waitForStatus(DISCOVERED, discoveryInterval + WAIT_OFFSET)
    }
}
