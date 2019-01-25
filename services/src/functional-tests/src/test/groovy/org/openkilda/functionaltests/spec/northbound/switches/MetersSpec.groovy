package org.openkilda.functionaltests.spec.northbound.switches

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.meter.SwitchMeterEntries
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException
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

    @Shared
    def centecs

    @Shared
    def nonCentecs

    def setupOnce() {
        //TODO: remove as soon as OVS 2.10 + kernel 4.18+ get wired in and meters support will be available
        // on virtual environments
        requireProfiles("hardware")
    }

    def "Able to delete a meter from a switch"() {
        setup: "Select a switch and retrieve default meters"
        def sw = topology.getActiveSwitches()[0]
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "A flow is created and its meter is deleted"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterToDelete = northbound.getAllMeters(sw.dpId).meterEntries.find {
            !defaultMeters.meterEntries*.meterId.contains(it.meterId)
        }.meterId
        def deleteResult = northbound.deleteMeter(sw.dpId, meterToDelete)

        then: "Delete operation should be successful"
        deleteResult.deleted

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check if no excessive meters are installed on the switch"
        defaultMeters.meterEntries == northbound.getAllMeters(sw.dpId).meterEntries
    }

    @Unroll
    def "Unable to delete a meter with invalid ID=#meterId on a #switchType switch"() {
        when: "Try to delete meter with invalid ID"
        northbound.deleteMeter(sw.getDpId(), -1)

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
        requireProfiles("hardware")

        setup: "Ensure the hardware topology is used and collect all Centec switches"
        def switches = getCentecSwitches()

        when: "Try to get all meters from the switches"
        List<SwitchMeterEntries> meters = switches.collect { northbound.getAllMeters(it.dpId) }

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
        requireProfiles("hardware") //TODO: Research how this behaves on OpenVSwitch

        given: "All Openflow 1.3 compatible switches"
        def switches = getNonCentecSwitches()

        when: "Try to get meters from all the switches"
        List<SwitchMeterEntries> meters = switches.collect { northbound.getAllMeters(it.dpId) }

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

    @Unroll
    def "Meters are created/deleted when creating/deleting a flow (ignore_bandwidth=#ignoreBandwidth)"() {
        given: "A switch with OpenFlow 1.3 support"
        def sw = topology.getActiveSwitches().find { it.ofVersion == "OF_13" }

        when: "Get default meters from the switch"
        def defaultMeters = northbound.getAllMeters(sw.dpId)
        assert defaultMeters

        and: "Create a flow"
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.ignoreBandwidth = ignoreBandwidth
        flowHelper.addFlow(flow)

        then: "New meters should appear after flow setup"
        def newMeters = northbound.getAllMeters(sw.dpId)
        def newMeterEntries = newMeters.meterEntries.findAll { !defaultMeters.meterEntries.contains(it) }
        newMeterEntries.size() == 2

        and: "All new meters should have KBPS, BURST and STATS flags installed"
        newMeterEntries.every { it.flags.sort().equals(["KBPS", "BURST", "STATS"].sort()) }

        and: "All new meters rate should be equal to flow's rate"
        newMeterEntries*.rate.every { it == flow.maximumBandwidth }

        when: "Delete a flow"
        flowHelper.deleteFlow(flow.id)

        then: "New meters should disappear from the switch"
        def newestMeters = northbound.getAllMeters(sw.dpId)
        newestMeters.meterEntries.containsAll(defaultMeters.meterEntries)
        newestMeters.meterEntries.size() == defaultMeters.meterEntries.size()

        where:
        ignoreBandwidth << [false, true]
    }

    def "Meters are not created when creating a flow with maximum_bandwidth=0"() {
        given: "A switch with OpenFlow 1.3 support"
        def sw = topology.getActiveSwitches().find { it.ofVersion == "OF_13" }

        when: "Get default meters from the switch"
        def defaultMeters = northbound.getAllMeters(sw.dpId)
        assert defaultMeters

        and: "Create a flow with maximum_bandwidth = 0"
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.maximumBandwidth = 0
        flowHelper.addFlow(flow)

        then: "Ony default meters should be present on the switch and new meters should not appear after flow setup"
        def newMeters = northbound.getAllMeters(sw.dpId)
        def newMeterEntries = newMeters.meterEntries.findAll { !defaultMeters.meterEntries.contains(it) }
        newMeterEntries.empty

        and: "Delete a flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Unroll
    def "Meter burst size should not exceed 105% of #flowRate kbps on non-Centec switches"() {
        requireProfiles("hardware") //TODO: Research how this behaves on OpenVSwitch

        setup: "A flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible switch"
        def sw = getNonCentecSwitches()[0]
        def defaultMeters = northbound.getAllMeters(sw.dpId)
        def flowPayload = flowHelper.singleSwitchFlow(sw)
        flowPayload.setMaximumBandwidth(100)
        def flow = flowHelper.addFlow(flowPayload)

        when: "Update flow bandwidth to #flowRate kbps"
        flow = northbound.getFlow(flow.id)
        flow.setMaximumBandwidth(flowRate)
        northbound.updateFlow(flow.id, flow)

        then: "New meters should be installed on the switch"
        def newMeters = null

        Wrappers.wait(Constants.RULES_DELETION_TIME + Constants.RULES_INSTALLATION_TIME) {
            newMeters = northbound.getAllMeters(sw.dpId).meterEntries.findAll {
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
        flowHelper.deleteFlow(flow.id)

        where:
        flowRate << [150, 1000, 1024, 5120, 10240, 2480, 960000]
    }

    @Unroll
    def "Flow burst should be set to #burstSize kbps on Centec switches in case of #flowRate kbps flow bandwidth"() {
        requireProfiles("hardware")

        setup: "A flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible switch"
        def sw = getCentecSwitches()[0]
        def defaultMeters = northbound.getAllMeters(sw.dpId)
        def flowPayload = flowHelper.singleSwitchFlow(sw)
        flowPayload.setMaximumBandwidth(100)
        def flow = flowHelper.addFlow(flowPayload)

        when: "Update flow bandwidth to #flowRate kbps"
        flow = northbound.getFlow(flow.id)
        flow.setMaximumBandwidth(flowRate)
        northbound.updateFlow(flow.id, flow)

        then: "New meters should be installed on the switch"
        def newMeters = null
        Wrappers.wait(Constants.RULES_DELETION_TIME + Constants.RULES_INSTALLATION_TIME) {
            newMeters = northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                !defaultMeters.meterEntries.contains(it)
            }
            assert newMeters.size() == 2
        }
        and: "New meters rate should be equal to flow bandwidth"
        newMeters*.rate.every { it == flowRate }

        and: "New meters burst size should be 1024 kbit/s regardless the flow speed"
        newMeters*.burstSize.every { it == burstSize }

        cleanup: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        flowRate | burstSize
        975      | 1024 //should have been 1023, but use the minimum 1024
        1000     | 1050 //the 1.05 multiplier
        30478    | 32000 //should have been 32001, but use the maximum 32000
    }

    List<Switch> getNonCentecSwitches() {
        if (!nonCentecs) {
            def nonCentecSw = northbound.getActiveSwitches().findAll {
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
}
