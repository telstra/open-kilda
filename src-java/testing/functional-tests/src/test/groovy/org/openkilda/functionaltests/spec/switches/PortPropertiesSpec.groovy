package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL

import org.openkilda.functionaltests.error.PortNotFoundExpectedError
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError

import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.model.PortProperties.DISCOVERY_ENABLED_DEFAULT
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See

import java.util.concurrent.TimeUnit

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
        assumeFalse(topology.notConnectedIsls.empty, "Need at least one not connected a-switch link")
        def isl = topology.notConnectedIsls.first()
        with(northboundV2.getPortProperties(isl.srcSwitch.dpId, isl.srcPort)) {
            it.switchId == isl.srcSwitch.dpId
            it.portNumber == isl.srcPort
            it.discoveryEnabled == DISCOVERY_ENABLED_DEFAULT
        }

        when: "Update port discovery property"
        PortPropertiesDto newPortProperties = new PortPropertiesDto()
        def newDiscoveryEnabled = !DISCOVERY_ENABLED_DEFAULT
        newPortProperties.discoveryEnabled = newDiscoveryEnabled
        def updatePortPropertiesResponse = northboundV2.updatePortProperties(isl.srcSwitch.dpId, isl.srcPort,
                newPortProperties)

        then: "Correct response is returned"
        verifyAll(updatePortPropertiesResponse) {
            it.switchId == isl.srcSwitch.dpId
            it.portNumber == isl.srcPort
            it.discoveryEnabled == newDiscoveryEnabled
        }

        and: "Port discovery property is really updated"
        Wrappers.wait(WAIT_OFFSET / 2) {
            with(northboundV2.getPortProperties(isl.srcSwitch.dpId, isl.srcPort)) {
                it.switchId == isl.srcSwitch.dpId
                it.portNumber == isl.srcPort
                it.discoveryEnabled == newDiscoveryEnabled
            }
        }

        cleanup: "Restore init port discovery property on the port"
        isl && northboundV2.updatePortProperties(isl.srcSwitch.dpId, isl.srcPort,
                new PortPropertiesDto(discoveryEnabled: DISCOVERY_ENABLED_DEFAULT))
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
        def sw = topology.activeSwitches.first()
        def nonExistentPort = 99999
        def response = northboundV2.getPortProperties(sw.dpId, nonExistentPort)

        then: "No error, default port propreties information is returned"
        with(response) {
            it.switchId == sw.dpId
            it.portNumber == nonExistentPort
            it.discoveryEnabled == DISCOVERY_ENABLED_DEFAULT
        }

        when: "Try to update port discovery property for non-existing port"
        northboundV2.updatePortProperties(sw.dpId, nonExistentPort, new PortPropertiesDto(discoveryEnabled: true))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new PortNotFoundExpectedError(sw.dpId, nonExistentPort, ~/Port not found exception/).matches(exc)    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System doesn't discover link when port discovery property is disabled"() {
        given: "A deleted link"
        def sw = topology.activeSwitches.first()
        def relatedIsls = topology.getRelatedIsls(sw)
        def islToManipulate = relatedIsls.first()
        def isRtl = [islToManipulate.srcSwitch, islToManipulate.dstSwitch]
                .any { it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }

        // Bring port down on the src switch
        def portDown = antiflap.portDown(islToManipulate.srcSwitch.dpId, islToManipulate.srcPort)
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(islToManipulate).actualState == IslChangeType.FAILED
        }

        // delete link
        northbound.deleteLink(islUtils.toLinkParameters(islToManipulate))
        !islUtils.getIslInfo(islToManipulate)
        !islUtils.getIslInfo(islToManipulate.reversed)

        when: "Disable port discovery property on the src and dst switches"
        def srcPortDiscoveryOff = disableDiscoveryOnPort(islToManipulate.srcSwitch.dpId, islToManipulate.srcPort)
        def dstPortDiscoveryOff = disableDiscoveryOnPort(islToManipulate.dstSwitch.dpId, islToManipulate.dstPort)

        and: "Bring port up on the src switch"
        def portUp = antiflap.portUp(islToManipulate.srcSwitch.dpId, islToManipulate.srcPort)

        then: "Link is not detected"
        Wrappers.timedLoop(discoveryInterval + WAIT_OFFSET / 2) {
            assert !islUtils.getIslInfo(islToManipulate).isPresent()
        }

        when: "Deactivate/activate src switch"
        def blockData = lockKeeper.knockoutSwitch(sw, RW)
        def switchStatus
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            switchStatus = northbound.getSwitch(sw.dpId).state
            assert switchStatus == SwitchChangeType.DEACTIVATED
        }

        lockKeeper.reviveSwitch(sw, blockData)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            switchStatus = northbound.getSwitch(sw.dpId).state
            assert switchStatus == SwitchChangeType.ACTIVATED
            def links = northbound.getAllLinks()
            (relatedIsls - islToManipulate).forEach {
                assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED
            }
        }

        then: "Link is still not detected"
        Wrappers.timedLoop(discoveryInterval) {
            assert !islUtils.getIslInfo(islToManipulate).isPresent()
        }

        when: "Enable port discovery property on the src switch"
        def srcPortDiscoveryOn = enableDiscoveryOnPort(islToManipulate.srcSwitch.dpId, islToManipulate.srcPort)

        then: "Link is detected and status of one-way ISL is FAILED"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            def islInfoForward = islUtils.getIslInfo(allLinks, islToManipulate).get()
            def islInfoReverse = islUtils.getIslInfo(allLinks, islToManipulate.reversed).get()
            assert islInfoForward.state == (isRtl ? IslChangeType.DISCOVERED : IslChangeType.FAILED)
            assert islInfoForward.actualState == IslChangeType.DISCOVERED
            assert islInfoReverse.state == (isRtl ? IslChangeType.DISCOVERED : IslChangeType.FAILED)
            assert islInfoReverse.actualState == IslChangeType.FAILED
        }

        when: "Enable port discovery property on the dst switch"
        def dstPortDiscoveryOn = enableDiscoveryOnPort(islToManipulate.dstSwitch.dpId, islToManipulate.dstPort)

        then: "Link status is changed to DISCOVERED"
        Wrappers.wait(WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToManipulate).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(islToManipulate.reversed).get().state == IslChangeType.DISCOVERED
        }
        cleanup:
        portDown && !portUp && antiflap.portUp(islToManipulate.srcSwitch.dpId, islToManipulate.srcPort)
        srcPortDiscoveryOff && !srcPortDiscoveryOn && enableDiscoveryOnPort(islToManipulate.srcSwitch.dpId, islToManipulate.srcPort)
        dstPortDiscoveryOff && !dstPortDiscoveryOn && enableDiscoveryOnPort(islToManipulate.dstSwitch.dpId, islToManipulate.dstPort)
        switchStatus && switchStatus == SwitchChangeType.DEACTIVATED && switchHelper.reviveSwitch(sw, blockData, true)
    }

    @Tags([SMOKE, SMOKE_SWITCHES])
    def "Link is stopped from being discovered after disabling port discovery property"() {
        given: "An active link"
        def islToManipulate = topology.islsForActiveSwitches.first()
        def isRtl = [islToManipulate.srcSwitch, islToManipulate.dstSwitch]
                .any { it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }

        when: "Disable port discovery property on the dst switch"
        northboundV2.updatePortProperties(islToManipulate.dstSwitch.dpId, islToManipulate.dstPort,
                new PortPropertiesDto(discoveryEnabled: false))

        then: "One-way ISL status is changed to FAILED"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            def islInfoForward = islUtils.getIslInfo(allLinks, islToManipulate).get()
            def islInfoReverse = islUtils.getIslInfo(allLinks, islToManipulate.reversed).get()
            assert islInfoForward.state == (isRtl ? IslChangeType.DISCOVERED : IslChangeType.FAILED)
            assert islInfoForward.actualState == IslChangeType.DISCOVERED
            assert islInfoReverse.state == (isRtl ? IslChangeType.DISCOVERED : IslChangeType.FAILED)
            assert islInfoReverse.actualState == IslChangeType.FAILED
        }

        when: "Enable port discovery property on the dst switch"
        northboundV2.updatePortProperties(islToManipulate.dstSwitch.dpId, islToManipulate.dstPort,
                new PortPropertiesDto(discoveryEnabled: true))

        then: "Link state is changed to DISCOVERED"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToManipulate.reversed).get().state == IslChangeType.DISCOVERED
        }
        Boolean testIsCompleted = true

        cleanup: "Restore discovery port property on the port"
        if (!testIsCompleted) {
            northboundV2.updatePortProperties(islToManipulate.dstSwitch.dpId, islToManipulate.dstPort,
                    new PortPropertiesDto(discoveryEnabled: DISCOVERY_ENABLED_DEFAULT))
            Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
                assert islUtils.getIslInfo(islToManipulate).get().state == IslChangeType.DISCOVERED
            }
        }
    }

    private PortPropertiesResponse enableDiscoveryOnPort(SwitchId switchId, Integer port) {
        northboundV2.updatePortProperties(switchId, port, new PortPropertiesDto(discoveryEnabled: true))
    }

    private PortPropertiesResponse disableDiscoveryOnPort(SwitchId switchId, Integer port) {
        northboundV2.updatePortProperties(switchId, port, new PortPropertiesDto(discoveryEnabled: false))
    }
}
