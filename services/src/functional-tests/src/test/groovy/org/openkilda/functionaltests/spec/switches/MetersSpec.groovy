package org.openkilda.functionaltests.spec.switches

import groovy.transform.Memoized
import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.meter.MeterEntry
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.info.rule.SwitchFlowEntries
import org.openkilda.model.SwitchId
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

import java.math.RoundingMode

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static spock.util.matcher.HamcrestSupport.expect

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

    @Unroll
    def "Able to delete a meter from a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "A flow is created and its meter is deleted"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterToDelete = northbound.getAllMeters(sw.dpId).meterEntries.find {
            !defaultMeters.meterEntries*.meterId.contains(it.meterId)
        }.meterId
        def deleteResult = northbound.deleteMeter(sw.dpId, meterToDelete)

        then: "Delete operation should be successful"
        deleteResult.deleted
        !northbound.getAllMeters(sw.dpId).meterEntries.find { it.meterId == meterToDelete }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check if no excessive meters are installed on the switch"
        defaultMeters.meterEntries == northbound.getAllMeters(sw.dpId).meterEntries

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    def "Unable to delete a meter with invalid ID=#meterId on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        when: "Try to delete meter with invalid ID"
        northbound.deleteMeter(switches[0].dpId, meterId)

        then: "Got BadRequest because meter ID is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400

        where:
        meterId | switches               | switchType
        -1      | getNonCentecSwitches() | "non-Centec"
        0       | getNonCentecSwitches() | "non-Centec"
        -1      | getCentecSwitches()    | "Centec"
        0       | getCentecSwitches()    | "Centec"
    }

    /**
     * Default meters should be set in PKTPS by default in Kilda, but Centec switches only allow KBPS flag.
     * System should recalculate the PKTPS value to KBPS on Centec switches.
     */
    def "Default meters should express bandwidth in kbps re-calculated from pktps on Centec switches"() {
        requireProfiles("hardware")

        setup: "Collect all Centec switches"
        def switches = getCentecSwitches()
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        expect: "Only the default meters should be present on each switch"
        switches.each { sw ->
            def meters = northbound.getAllMeters(sw.dpId)
            assert meters.meterEntries.size() == 2
            assert meters.meterEntries.every { Math.abs(it.rate - (DISCO_PKT_RATE * DISCO_PKT_SIZE) / 1024L) <= 1 }
            //unable to use #getExpectedBurst. For Centects there's special burst due to KBPS
            assert meters.meterEntries.every { it.burstSize == (long) ((DISCO_PKT_BURST * DISCO_PKT_SIZE) / 1024) }
            assert meters.meterEntries.every(defaultMeters)
            assert meters.meterEntries.every { ["KBPS", "BURST", "STATS"].containsAll(it.flags) }
            assert meters.meterEntries.every { it.flags.size() == 3 }

        }
    }

    def "Default meters should express bandwidth in pktps on non-Centec switches"() {
        requireProfiles("hardware") //TODO: Research how this behaves on OpenVSwitch

        given: "All Openflow 1.3 compatible non-Centec switches"
        def switches = getNonCentecSwitches()
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        expect: "Only the default meters should be present on each switch"
        switches.each { sw ->
            def meters = northbound.getAllMeters(sw.dpId)
            assert meters.meterEntries.size() == 2
            assert meters.meterEntries.every { it.rate == DISCO_PKT_RATE }
            assert meters.meterEntries.every { it.burstSize == getExpectedBurst(sw.dpId, DISCO_PKT_RATE) }
            assert meters.meterEntries.every(defaultMeters)
            assert meters.meterEntries.every { ["PKTPS", "BURST", "STATS"].containsAll(it.flags) }
            assert meters.meterEntries.every { it.flags.size() == 3 }
        }
    }

    @Unroll
    def "Meters are created/deleted when creating/deleting a single-switch flow with ignore_bandwidth=#ignoreBandwidth \
on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        given: "A #switchType switch with OpenFlow 1.3 support"
        def sw = switches.first()

        when: "Get default meters from the switch"
        def defaultMeters = northbound.getAllMeters(sw.dpId)
        assert defaultMeters

        and: "Create a single-switch flow"
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

        and: "Switch validation shows no discrepancies in meters"
        def metersValidation = northbound.switchValidate(sw.dpId).meters
        metersValidation.proper.size() == 2
        metersValidation.excess.empty
        metersValidation.missing.empty
        metersValidation.misconfigured.empty

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "New meters should disappear from the switch"
        def newestMeters = northbound.getAllMeters(sw.dpId)
        newestMeters.meterEntries.containsAll(defaultMeters.meterEntries)
        newestMeters.meterEntries.size() == defaultMeters.meterEntries.size()

        where:
        switchType   | switches               | ignoreBandwidth
        "Centec"     | getCentecSwitches()    | false
        "Centec"     | getCentecSwitches()    | true
        "non-Centec" | getNonCentecSwitches() | false
        "non-Centec" | getNonCentecSwitches() | true
    }

    @Unroll
    def "Meters are not created when creating a single-switch flow with maximum_bandwidth=0 on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        given: "A #switchType switch with OpenFlow 1.3 support"
        def sw = switches.first()

        when: "Get default meters from the switch"
        def defaultMeters = northbound.getAllMeters(sw.dpId)
        assert defaultMeters

        and: "Create a single-switch flow with maximum_bandwidth=0"
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.maximumBandwidth = 0
        flow.ignoreBandwidth = true
        flowHelper.addFlow(flow)

        then: "Ony default meters should be present on the switch and new meters should not appear after flow setup"
        def newMeters = northbound.getAllMeters(sw.dpId)
        def newMeterEntries = newMeters.meterEntries.findAll { !defaultMeters.meterEntries.contains(it) }
        newMeterEntries.empty

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    def "Source/destination switches have meters only in flow ingress rule and intermediate switches don't have \
meters in flow rules at all (#flowType flow)"() {
        assumeTrue("Unable to find required switches in topology", switches.size() > 1)

        given: "Two active not neighboring switches (#flowType)"
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) } &&
                    (flowType == "Centec-nonCentec" ? (src.centec && !dst.centec) || (!src.centec && dst.centec) : true)
        } ?: assumeTrue("No suiting switch pair with intermediate switch found", false)

        when: "Create a flow between these switches"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "The source and destination switches have only one meter in the flow's ingress rule"
        def srcSwFlowMeters = northbound.getAllMeters(srcSwitch.dpId).meterEntries.findAll(flowMeters)
        def dstSwFlowMeters = northbound.getAllMeters(dstSwitch.dpId).meterEntries.findAll(flowMeters)

        srcSwFlowMeters.size() == 1
        dstSwFlowMeters.size() == 1

        def srcSwitchRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
        def dstSwitchRules = northbound.getSwitchRules(dstSwitch.dpId).flowEntries
        def srcSwFlowIngressRule = filterRules(srcSwitchRules, flow.source.portNumber, flow.source.vlanId, null)[0]
        def dstSwFlowIngressRule = filterRules(dstSwitchRules, flow.destination.portNumber, flow.destination.vlanId,
                null)[0]

        srcSwFlowMeters[0].meterId == srcSwFlowIngressRule.instructions.goToMeter
        dstSwFlowMeters[0].meterId == dstSwFlowIngressRule.instructions.goToMeter

        and: "The source and destination switches have no meters in the flow's egress rule"
        def srcSwFlowEgressRule = filterRules(srcSwitchRules, null, null, flow.source.portNumber)[0]
        def dstSwFlowEgressRule = filterRules(dstSwitchRules, null, null, flow.destination.portNumber)[0]

        !srcSwFlowEgressRule.instructions.goToMeter
        !dstSwFlowEgressRule.instructions.goToMeter

        and: "Intermediate switches don't have meters in flow rules at all"
        pathHelper.getInvolvedSwitches(flow.id)[1..-2].each { sw ->
            assert northbound.getAllMeters(sw.dpId).meterEntries.findAll(flowMeters).empty

            def flowRules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll { !(it.cookie in sw.defaultCookies) }
            flowRules.each { assert !it.instructions.goToMeter }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        flowType              | switches
        "Centec-Centec"       | getCentecSwitches()
        "nonCentec-nonCentec" | getNonCentecSwitches()
        "Centec-nonCentec"    | getCentecSwitches() + getNonCentecSwitches()
    }

    @Unroll
    def "Meter burst size is correctly set on non-Centec switches for #flowRate flow rate"() {
        requireProfiles("hardware") //TODO: Research how this behaves on OpenVSwitch

        setup: "A single-switch flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible switch"
        def switches = getNonCentecSwitches()
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        def sw = switches.first()
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

        and: "New meters burst size matches the expected value for given switch model"
        newMeters*.burstSize.each { assert it == getExpectedBurst(sw.dpId, flowRate) }

        and: "Switch validation shows no discrepancies in meters"
        def metersValidation = northbound.switchValidate(sw.dpId).meters
        metersValidation.proper.size() == 2
        metersValidation.excess.empty
        metersValidation.missing.empty
        metersValidation.misconfigured.empty

        cleanup: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        flowRate << [150, 1000, 1024, 5120, 10240, 2480, 960000]
    }

    @Unroll("Flow burst should be correctly set on Centec switches in case of #flowRate kbps flow bandwidth")
    def "Flow burst is correctly set on Centec switches"() {
        requireProfiles("hardware")

        setup: "A single-switch flow with #flowRate kbps bandwidth is created on OpenFlow 1.3 compatible Centec switch"
        def switches = getCentecSwitches()
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        def sw = switches.first()
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

        and: "New meters burst size should respect the min/max border value for Centec"
        newMeters*.burstSize.every { it == expectedBurstSize }

        and: "Switch validation shows no discrepancies in meters"
        def metersValidation = northbound.switchValidate(sw.dpId).meters
        metersValidation.proper.size() == 2
        metersValidation.excess.empty
        metersValidation.missing.empty
        metersValidation.misconfigured.empty

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

        given: "Switches combination (#data.description)"
        assumeTrue("Desired switch combination is not available in current topology", data.switches.size() > 1)
        def (Switch src, Switch dst) = data.switches[0..1]

        and: "A flow with custom meter rate and burst, that differ from defaults"
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
                        switches   : nonCentecSwitches
                ],
                [
                        description: "Centec-Centec",
                        switches   : centecSwitches
                ],
                [
                        description: "Centec-nonCentec",
                        switches   : !centecSwitches.empty && !nonCentecSwitches.empty ?
                                [centecSwitches[0], nonCentecSwitches[0]] : []
                ]
        ]
    }

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

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Can't update meter: Flow '$flow.id' is unmetered"

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
    String getSwitchDescription(SwitchId sw) {
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

    List<FlowEntry> filterRules(List<FlowEntry> rules, inPort, inVlan, outPort) {
        if (inPort) {
            rules = rules.findAll { it.match.inPort == inPort.toString() }
        }
        if (inVlan) {
            rules = rules.findAll { it.match.vlanVid == inVlan.toString() }
        }
        if (outPort) {
            rules = rules.findAll { it.instructions?.applyActions?.flowOutput == outPort.toString() }
        }

        return rules
    }

    def defaultMeters = { it.meterId <= MAX_SYSTEM_RULE_METER_ID }

    def flowMeters = { it.meterId > MAX_SYSTEM_RULE_METER_ID }
}
