package org.openkilda.functionaltests.spec.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.meter.MeterEntry
import org.openkilda.messaging.info.meter.SwitchMeterEntries
import org.openkilda.messaging.info.rule.SwitchFlowEntries
import org.openkilda.model.SwitchId
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Unroll

import java.math.RoundingMode

@Narrative("The test suite checks if traffic meters, including default, are set and deleted in a correct way.")
class MetersSpec extends BaseSpecification {

    static DISCO_PKT_RATE = 200 // Number of packets per second for the default flows
    static DISCO_PKT_SIZE = 250 // Default size of the discovery packet
    static NOVIFLOW_BURST_COEFFICIENT = 1.005 // Driven by the Noviflow specification
    static DISCO_PKT_BURST = 4096 // Default desired packet burst rate for the default flows (ignored by Noviflow)
    static CENTEC_MIN_BURST = 1024 // Driven by the Centec specification
    static CENTEC_MAX_BURST = 32000 // Driven by the Centec specification

    @Value('${burst.coefficient}')
    double burstCoefficient

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

    /**
     * Default meters should be set in PKTPS by default in Kilda, but Centec switches only allow KBPS flag.
     * System should recalculate the PKTPS value to KBPS on Centec switches.
     */
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
            assert it.meterEntries.every { it.meterId <= MAX_SYSTEM_RULE_METER_ID }
            //unable to use #getExpectedBurst. For Centects there's special burst due to KBPS
            assert it.meterEntries.every { it.burstSize == (long) ((DISCO_PKT_BURST * DISCO_PKT_SIZE) / 1024) }
            assert it.meterEntries.every { ["KBPS", "BURST", "STATS"].containsAll(it.flags) }
            assert it.meterEntries.every { it.flags.size() == 3 }

        }
    }

    def "Default meters should express bandwidth in packet/s rather than kbps on the non-Centec switches"() {
        requireProfiles("hardware") //TODO: Research how this behaves on OpenVSwitch

        given: "All Openflow 1.3 compatible switches"
        def switches = getNonCentecSwitches()

        expect: "Only the default meters should be present on each switch"
        switches.each { sw ->
            def meters = northbound.getAllMeters(sw.dpId)
            assert meters.meterEntries.size() == 2
            assert meters.meterEntries.every { it.rate == DISCO_PKT_RATE }
            assert meters.meterEntries.every {
                it.burstSize == getExpectedBurst(sw.dpId, DISCO_PKT_RATE)
            }
            assert meters.meterEntries.every { it.meterId <= MAX_SYSTEM_RULE_METER_ID }
            assert meters.meterEntries.every { ["PKTPS", "BURST", "STATS"].containsAll(it.flags) }
            assert meters.meterEntries.every { it.flags.size() == 3 }
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
        newMeters*.burstSize.each {
            assert it == getExpectedBurst(sw.dpId, flowRate)
        }

        cleanup: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        flowRate << [150, 1000, 1024, 5120, 10240, 2480, 960000]
    }

    @Unroll("Flow burst should be correctly set on Centec switches in case of #flowRate kbps flow bandwidth")
    def "Flow burst is correctly set on Centec switches"() {
        requireProfiles("hardware")

        setup: "A flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible Centec switch"
        def sw = getCentecSwitches()[0]
        def expectedBurstSize = getExpectedBurst(sw.dpId, flowRate)
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
        newMeters*.burstSize.every { it == expectedBurstSize }

        cleanup: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        flowRate << [
                //flowRate below min
                ((CENTEC_MIN_BURST - 1) / getBurstCoefficient())
                        .toBigDecimal().setScale(0, RoundingMode.CEILING).toLong(),
                1000, //casual middle value
                //flowRate above max
                ((CENTEC_MAX_BURST + 1) / getBurstCoefficient())
                        .toBigDecimal().setScale(0, RoundingMode.CEILING).toLong()
        ]
    }

    @Unroll
    def "System allows to reset meter values to defaults without reinstalling rules for #data.description flow"() {
        requireProfiles("hardware")

        given: "A flow with custom meter rate and burst, that differ from defaults"
        def src = data.srcSwitch
        def dst = data.dstSwitch
        assumeTrue("Desired switch combination is not available in current topology", src && dst)
        def flow = flowHelper.randomFlow(src, dst)
        flow.maximumBandwidth = 1000
        flowHelper.addFlow(flow)
        /*at this point meters are set for given flow. Now update flow bandwidth directly via DB, so that existing meter
        rate and burst is no longer correspond to the flow bandwidth*/
        def newBandwidth = 2000
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow
        //now save some original data for further comparison before resetting meters
        Map<SwitchId, SwitchFlowEntries> originalRules = [src.dpId, dst.dpId].collectEntries {
            [(it): northbound.getSwitchRules(it)]
        }
        Map<SwitchId, List<MeterEntry>> originalMeters = [src.dpId, dst.dpId].collectEntries {
            [(it): northbound.getAllMeters(it).meterEntries]
        }
        def defaultMeters = { it.meterId < MAX_SYSTEM_RULE_METER_ID }
        def flowMeters = { it.meterId >= MAX_SYSTEM_RULE_METER_ID }

        when: "Ask system to reset meters for the flow"
        def response = northbound.resetMeters(flow.id)

        then: "Response contains correct info about new meter values"
        [response.srcMeter, response.dstMeter].each { switchMeterEntries ->
            def originalFlowMeters = originalMeters[switchMeterEntries.switchId].findAll(flowMeters)
            switchMeterEntries.meterEntries.each { meterEntry ->
                assert meterEntry.rate == newBandwidth
                assert meterEntry.burstSize == getExpectedBurst(switchMeterEntries.switchId, newBandwidth)
            }
            assert switchMeterEntries.meterEntries*.meterId.sort() == originalFlowMeters*.meterId.sort()
            assert switchMeterEntries.meterEntries*.flags.sort() == originalFlowMeters*.flags.sort()
        }

        and: "Non-default meter rate and burst are actually changed to expected values both on src and dst switch"
        def srcFlowMeters = northbound.getAllMeters(src.dpId).meterEntries.findAll(flowMeters)
        def dstFlowMeters = northbound.getAllMeters(dst.dpId).meterEntries.findAll(flowMeters)
        expect srcFlowMeters, sameBeanAs(response.srcMeter.meterEntries).ignoring("timestamp")
        expect dstFlowMeters, sameBeanAs(response.dstMeter.meterEntries).ignoring("timestamp")

        and: "Default meters are unchanged"
        [src.dpId, dst.dpId].each { SwitchId swId ->
            assert expect(northbound.getAllMeters(swId).meterEntries.findAll(defaultMeters).sort(),
                    sameBeanAs(originalMeters[swId].findAll(defaultMeters).sort())
                            .ignoring("timestamp"))
        }

        and: "Switch rules are unchanged"
        [src.dpId, dst.dpId].each { SwitchId swId ->
            assert expect(northbound.getSwitchRules(swId), sameBeanAs(originalRules[swId])
                    .ignoring("timestamp")
                    .ignoring("flowEntries.durationNanoSeconds")
                    .ignoring("flowEntries.durationSeconds")
                    .ignoring("flowEntries.byteCount")
                    .ignoring("flowEntries.packetCount"))
        }

        and: "Cleanup: delete flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << [
                [
                        description: "nonCentec-nonCentec",
                        srcSwitch  : nonCentecSwitches[0],
                        dstSwitch  : nonCentecSwitches[1]
                ],
                [
                        description: "centec-centec",
                        srcSwitch  : centecSwitches[0],
                        dstSwitch  : centecSwitches[1]
                ],
                [
                        description: "centec-nonCentec",
                        srcSwitch  : centecSwitches[0],
                        dstSwitch  : nonCentecSwitches[0]
                ]
        ]
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/2043")
    def "Try to reset meters for unmetered flow"() {
        given: "A flow with the 'bandwidth: 0' and 'ignoreBandwidth: true' fields"
        def availableSwitches = topology.activeSwitches
        def src = availableSwitches[0]
        def dst = availableSwitches[1]

        def flow = flowHelper.randomFlow(src, dst)
        flow.ignoreBandwidth = true
        flow.maximumBandwidth = 0
        flowHelper.addFlow(flow)

        when: "Resetting meter burst and rate to default"
        northbound.resetMeters(flow.id)
//        TODO(andriidovhan) add then and check response: body or message

        then: "Delete the created flow"
        flowHelper.deleteFlow(flow.id)
    }

    /**
     * This method calculates expected burst for different types of switches. The common burst equals to
     * `rate * burstCoefficient`. There are couple exceptions though:<br>
     * <b>Noviflow</b>: Does not use our common burst coefficient and overrides it with its own coefficient (see static
     * variables at the top of the class).<br>
     * <b>Centec</b>: Follows our burst coefficient policy, except for restrictions for the minimum and maximum burst.
     * In cases when calculated burst is higher or lower of the Centec max/min - the max/min burst value will be used
     * instead.
     *
     * @param sw switchId where burst is being calculated. Needed to get the switch manufacturer and apply special
     * calculations if required
     * @param rate meter rate which is used to calculate burst
     * @return the expected burst value for given switch and rate
     */
    def getExpectedBurst(SwitchId sw, long rate) {
        def descr = getSwitchDescription(sw).toLowerCase()
        if (descr.contains("noviflow")) {
            return (rate * NOVIFLOW_BURST_COEFFICIENT - 1).setScale(0, RoundingMode.CEILING)
        } else if (descr.contains("centec")) {
            def burst = (rate * burstCoefficient).toBigDecimal().setScale(0, RoundingMode.FLOOR)
            if (burst <= CENTEC_MIN_BURST) {
                return CENTEC_MIN_BURST
            } else if (burst > CENTEC_MIN_BURST && burst <= CENTEC_MAX_BURST) {
                return burst
            } else {
                return CENTEC_MAX_BURST
            }
        } else {
            return (rate * burstCoefficient).round(0)
        }
    }

    @Memoized
    def getSwitchDescription(SwitchId sw) {
        northbound.activeSwitches.find { it.switchId == sw }.description
    }

    @Memoized
    List<Switch> getNonCentecSwitches() {
        topology.activeSwitches.findAll { !it.centec && it.ofVersion == "OF_13" }
    }

    @Memoized
    List<Switch> getCentecSwitches() {
        topology.getActiveSwitches().findAll { it.centec }
    }
}
