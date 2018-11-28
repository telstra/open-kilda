package org.openkilda.functionaltests.spec.northbound.switches

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.meter.SwitchMeterEntries
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

@Narrative("The test suite checks if traffic meters, including default, are set and deleted in a correct way.")
class MetersSpec extends BaseSpecification {

    static DISCO_PKT_RATE = 200 // Number of packets per second for the default flows
    static DISCO_PKT_SIZE = 250 // Default size of the discovery packet
    static NOVI_BURST_MIN = 1.004 // Guaranteed minimum flow burst rate on a Noviflow switch, with rounding errors.
    static NOVI_BURST_MAX = 1.051 // Possible maximum flow burst rate on a Noviflow switch, with rounding errors.
    static DISCO_PKT_BURST = 4096 // Default desired packet burst rate for the default flows (ignored by Noviflow)

    @Autowired
    TopologyDefinition topology

    @Autowired
    NorthboundService northboundService

    @Shared
    def centecs

    @Shared
    def nonCentecs

    def setupOnce() {
        this.requireProfiles("hardware") //TODO: remove as soon as OVS 2.10 + kernel 4.18+ get wired in
    }

    List<Switch> getNonCentecSwitches() {
        if (!nonCentecs) {
            def nonCentecSw = northboundService.getActiveSwitches().findAll {
                !it.description.toLowerCase().contains("centec")
            }*.switchId
            nonCentecs = topology.getActiveSwitches().findAll {
                (it.ofVersion == "OF_13") && (nonCentecSw.contains(it.dpId))
            }
        }
        return nonCentecs
    }

    List<Switch> getCentecSwitches() {
        if (!centecs) {
            centecs = topology.getActiveSwitches().findAll { it.ofVersion == "OF_13" } - getNonCentecSwitches()
        }
        return centecs
    }

    @Unroll
    def "Unable to delete meter with invalid ID #meterId on a #switchType switch"() {
        when: "Try to delete meter with invalid ID"
        northboundService.deleteMeter(sw.getDpId(), -1)

        then: "Got BadRequest because meter ID is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        where:
        meterId | sw                        | switchType
        -1      | getNonCentecSwitches()[0] | "non-Centec"
        0       | getCentecSwitches()[0]    | "Centec"
        -1      | getCentecSwitches()[0]    | "non-Centec"
        0       | getNonCentecSwitches()[0] | "Centec"
    }

    def "Default meters should be re-calculated to kbps on Centec switches"() {
        setup: "Ensure the hardware topology is used and collect all Centec switches"
        this.requireProfiles("hardware")
        def switches = getCentecSwitches()

        when: "Try to get all meters from the switches"
        List<SwitchMeterEntries> meters = switches.collect {
            northboundService.getAllMeters(it.dpId)
        }

        then: "Only the default meters should be present on each switch"
        meters.each {
            assert it.meterEntries.size() == 2
            assert it.meterEntries.every { Math.abs(it.rate - (DISCO_PKT_RATE * DISCO_PKT_SIZE) / 1024L) <= 1 }
            assert it.meterEntries.every { it.meterId <= Constants.MAX_DEFAULT_METER_ID }
            assert it.meterEntries.every { it.burstSize == (long) ((DISCO_PKT_BURST * DISCO_PKT_SIZE) / 1024) }
            assert it.meterEntries.every { ["KBPS", "BURST", "STATS"].containsAll(it.flags) }
            assert it.meterEntries.every { it.flags.size() == 3 }

        }
    }

    def "Default meters should express bandwidth in packet/s rather than kbps on the non-Centec switches"() {
        given: "All Openflow 1.3 compatible switches"
        this.requireProfiles("hardware") //TODO: Research how this behaves on OpenVSwitch
        def switches = getNonCentecSwitches()

        when: "Try to get meters from all the switches"
        List<SwitchMeterEntries> meters = switches.collect {
            northboundService.getAllMeters(it.dpId)
        }

        then: "Only the default meters should be present on each switch"
        meters.each {
            assert it.meterEntries.size() == 2
            assert it.meterEntries.every { it.rate == DISCO_PKT_RATE }
            assert it.meterEntries.every {
                //NoviFlow wouldn't let us set burst size to more than 105% of the flow bandwidth
                //effectively negating the default burst packet rate
                DISCO_PKT_RATE >= 4096 ? it.burstSize == DISCO_PKT_BURST :
                        (it.burstSize >= Math.floor(DISCO_PKT_RATE * NOVI_BURST_MIN)) &&
                                (it.burstSize <= Math.ceil(DISCO_PKT_RATE * NOVI_BURST_MAX))
            }
            assert it.meterEntries.every { it.meterId <= Constants.MAX_DEFAULT_METER_ID }
            assert it.meterEntries.every { ["PKTPS", "BURST", "STATS"].containsAll(it.flags) }
            assert it.meterEntries.every { it.flags.size() == 3 }
        }
    }

    def "Creating a flow should create new meters on a switch"() {
        given: "A switch with OpenFlow 1.3 support"
        def sw = topology.getActiveSwitches().find { it.ofVersion == "OF_13" }

        when: "Get default meters from the switch"
        def defaultMeters = northboundService.getAllMeters(sw.dpId)
        assert defaultMeters

        and: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))

        then: "New meters should appear after flow setup"
        def newMeters = northboundService.getAllMeters(sw.dpId)
        def newMeterEntries = newMeters.meterEntries.findAll { !defaultMeters.meterEntries.contains(it) }
        newMeterEntries.size() == 2

        and: "All new meters should have KBPS, BURST and STATS flags installed"
        newMeterEntries.every { it.flags.sort().equals(["KBPS", "BURST", "STATS"].sort()) }

        and: "All new meters rate should be equal to flow's rate"
        newMeterEntries*.rate.every { it == flow.maximumBandwidth }

        when: "Delete a flow"
        flowHelper.deleteFlow(flow.id)

        then: "New meters should disappear from the switch"
        def newestMeters = northboundService.getAllMeters(sw.dpId)
        newestMeters.meterEntries.containsAll(defaultMeters.meterEntries)
        newestMeters.meterEntries.size() == defaultMeters.meterEntries.size()
    }

    @Unroll
    def "Meter burst size should not exceed 105% of #flowRate kbps on non-Centec switches"() {
        setup: "A flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible switch"
        def sw = getNonCentecSwitches()[0]
        def defaultMeters = northboundService.getAllMeters(sw.dpId)
        def flowPayload = flowHelper.singleSwitchFlow(sw)
        flowPayload.setMaximumBandwidth(100)
        def flow = flowHelper.addFlow(flowPayload)

        when: "Update flow bandwidth to #flowRate kbps"
        flow = northboundService.getFlow(flow.id)
        flow.setMaximumBandwidth(flowRate)
        northboundService.updateFlow(flow.id, flow)

        then: "New meters should be installed on the switch"
        def newMeters = null

        Wrappers.wait(Constants.RULES_DELETION_TIME + Constants.RULES_INSTALLATION_TIME) {
            newMeters = northboundService.getAllMeters(sw.dpId).meterEntries.findAll {
                !defaultMeters.meterEntries.contains(it)
            }
            assert newMeters.size() == 2
        }

        and: "New meters rate should be equal to flow bandwidth"
        newMeters*.rate.every { it == flowRate }

        and: "New meters burst size should be between 100.5% and 105% of the flow's rate"
        newMeters*.burstSize.every {
            (it >= Math.floor(flowRate * NOVI_BURST_MIN)) && (it <= Math.ceil(flowRate * NOVI_BURST_MAX))
        }

        cleanup: "Delete the flow"
        northboundService.deleteFlow(flow.id)

        where:
        flowRate << [150, 1000, 1024, 5120, 10240, 2480, 960000]
    }

    def "Flow burst should be set to #burstSize kbps on Centec switches in case of #flowRate kbps flow bandwidth"() {
        setup: "A flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible switch"
        this.requireProfiles("hardware")
        def sw = getCentecSwitches()[0]
        def defaultMeters = northboundService.getAllMeters(sw.dpId)
        def flowPayload = flowHelper.singleSwitchFlow(sw)
        flowPayload.setMaximumBandwidth(100)
        def flow = flowHelper.addFlow(flowPayload)

        when: "Update flow bandwidth to #flowRate kbps"
        flow = northboundService.getFlow(flow.id)
        flow.setMaximumBandwidth(flowRate)
        northboundService.updateFlow(flow.id, flow)

        then: "New meters should be installed on the switch"
        def newMeters = null
        Wrappers.wait(Constants.RULES_DELETION_TIME + Constants.RULES_INSTALLATION_TIME) {
            newMeters = northboundService.getAllMeters(sw.dpId).meterEntries.findAll {
                !defaultMeters.meterEntries.contains(it)
            }
            assert newMeters.size() == 2
        }
        and: "New meters rate should be equal to flow bandwidth"
        newMeters*.rate.every { it == flowRate }

        and: "New meters burst size should be 1024 kbit/s regardless the flow speed"
        newMeters*.burstSize.every { it == burstSize }

        cleanup: "Delete the flow"
        northboundService.deleteFlow(flow.id)

        where:
        flowRate | burstSize
        100      | 1024
        1024     | 1024
        20480    | 1024
        960000   | 1024
    }

    @Ignore
    @Issue("https://github.com/telstra/open-kilda/issues/1733")
    def "Kilda should be able to delete a meter"() {
        setup: "Select a switch and retrieve default meters"
        def sw = topology.getActiveSwitches()[0]
        def defaultMeters = northboundService.getAllMeters(sw.dpId)

        when: "A flow is created and its meter is deleted"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterToDelete = northboundService.getAllMeters(sw.dpId).meterEntries.find {
            !defaultMeters.meterEntries*.meterId.contains(it.meterId)
        }.meterId
        def deleteResult = northboundService.deleteMeter(sw.dpId, meterToDelete)

        then: "Delete operation should be successful"
        deleteResult.deleted

        and: "Delete the flow"
        northboundService.deleteFlow(flow.id)

        and: "Check if no excessive meters are installed on the switch"
        defaultMeters.meterEntries == northboundService.getAllMeters(sw.dpId).meterEntries
    }
}