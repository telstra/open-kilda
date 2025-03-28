package org.openkilda.functionaltests.spec.links

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import spock.lang.See
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

@Tags([HARDWARE]) // virtual env doesn't support round trip latency
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/network-discovery")
class RoundTripIslSpec extends HealthCheckSpecification {

    @Shared
    List<SwitchExtended> switchesWithRtl

    def setupSpec() {
        switchesWithRtl = switches.all().withRtlSupport().getListOfSwitches()
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Isl with round-trip properly changes status after port events(#descr)"() {
        given: "Round-trip ISL with a-switch"
        def isl = isls.all().withASwitch().betweenSwitches(switchesWithRtl).random()
        isl.setBfd()

        when: "Port down event happens"
        isl.breakIt()

        and: "Port up event happens, but traffic goes only in one direction"
        aSwitchFlows.removeFlows([isl.getASwitch()])

        then: "ISL is not getting discovered"
        TimeUnit.SECONDS.sleep(discoveryInterval + 2)
        isl.getNbDetails().state == FAILED
        isl.reversed.getNbDetails().state == FAILED

        when: "Traffic starts to flow in both directions"
        aSwitchFlows.addFlows([isl.getASwitch()])

        then: "ISL gets discovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def fw = isl.getNbDetails()
            def rv = isl.reversed.getNbDetails()
            assert fw.state == DISCOVERED
            assert fw.actualState == DISCOVERED
            assert rv.state == DISCOVERED
            assert rv.actualState == DISCOVERED
        }

        where:
        bfd << [false, true]
        descr = "with${bfd ? '': 'out'} bfd"
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "A round trip latency ISL doesn't go down when one switch lose connection to FL"() {
        given: "A switch with/without round trip latency ISLs"
        def roundTripIsls
        def nonRoundTripIsls
        def swToDeactivate = switchesWithRtl.find { sw ->
            def swRelatedIsls = isls.all().relatedTo(sw).getListOfIsls()
            roundTripIsls = swRelatedIsls.findAll { it.dstSwId in switchesWithRtl.switchId }
            nonRoundTripIsls = swRelatedIsls.findAll { it.dstSwId !in switchesWithRtl.switchId }
            roundTripIsls && nonRoundTripIsls
        } ?: assumeTrue(false, "Wasn't able to find a switch with suitable links")

        when: "Simulate connection lose between the switch and FL, the switch becomes DEACTIVATED and remains operable"
        swToDeactivate.knockout(RW)

        and: "Wait discoveryTimeout"
        sleep(discoveryTimeout * 1000)

        then: "All non round trip latency ISLs are FAILED"
        Wrappers.wait(WAIT_OFFSET) {
            withPool {
                nonRoundTripIsls.eachParallel { assert it.getNbDetails().state == FAILED }
            }
        }

        and: "All round trip latency ISLs are still DISCOVERED (the system uses round trip latency status \
for ISL alive confirmation)"
        withPool {
            roundTripIsls.eachParallel { assert it.getNbDetails().state == DISCOVERED }
        }
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "A round trip latency ISL goes down when both switches lose connection to FL"() {
        given: "A round trip latency ISL"
        def roundTripIsl = isls.all().betweenSwitches(switchesWithRtl).first()

        when: "Switches lose connection to FL, switches become DEACTIVATED but keep processing packets"
        switchesWithRtl.find { it.switchId == roundTripIsl.srcSwId }.knockout(RW)
        switchesWithRtl.find { it.switchId == roundTripIsl.dstSwId }.knockout(RW)

        then: "The round trip latency ISL is FAILED (because round_trip_status is not available in DB for current ISL on both switches)"
        roundTripIsl.waitForStatus(FAILED)
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "A round trip latency ISL goes down when the src switch lose connection to FL and \
round trip latency rule is removed on the dst switch"() {
        given: "A round trip latency ISL"
        def roundTripIsl = isls.all().betweenSwitches(switchesWithRtl).first()
        def src =  switchesWithRtl.find { it.switchId == roundTripIsl.srcSwId }
        def dst =  switchesWithRtl.find { it.switchId == roundTripIsl.dstSwId }

        and: "Round trip status is ACTIVE for the given ISL in both directions"
        roundTripIsl.waitForStatus(DISCOVERED)

        when: "Simulate connection lose between the src switch and FL, switches become DEACTIVATED and remain operable"
        def mgmtBlockData = src.knockout(RW)

        then: "Round trip status for forward direction is not available and ACTIVE in reverse direction"
        roundTripIsl.waitForRoundTripStatus(FAILED, DISCOVERED, discoveryTimeout + WAIT_OFFSET / 2)

        when: "Delete ROUND_TRIP_LATENCY_RULE_COOKIE on the dst switch"
        dst.rulesManager.delete(DeleteRulesAction.REMOVE_ROUND_TRIP_LATENCY)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert dst.validate().rules.missing.size() == 1
        }

        then: "The round trip latency ISL is FAILED"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            assert roundTripIsl.getNbDetails().state == FAILED
        }

        and: "Round trip status is not available for the given ISL in both directions"
        roundTripIsl.waitForRoundTripStatus(FAILED)

        when: "Restore connection between the src switch and FL"
        src.revive(mgmtBlockData)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == DISCOVERED
            }.size() == topology.islsForActiveSwitches.size() * 2
        }

        then: "Round trip isl is DISCOVERED"
        roundTripIsl.getNbDetails().state == DISCOVERED

        and: "Round trip status is available for the given ISL in forward direction only"
        roundTripIsl.waitForRoundTripStatus(DISCOVERED, FAILED)

        when: "Install ROUND_TRIP_LATENCY_RULE_COOKIE on the dst switch"
        dst.rulesManager.install(InstallRulesAction.INSTALL_ROUND_TRIP_LATENCY)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert !dst.validateAndCollectFoundDiscrepancies().isPresent()
        }

        then: "Round trip status is available for the given ISL in both directions"
        roundTripIsl.waitForRoundTripStatus(DISCOVERED)
    }

    @Tags([SMOKE_SWITCHES])
    def "A round trip latency ISL goes down when portDiscovery property is disabled on the src/dst ports"() {
        given: "A round trip latency ISL"
        def roundTripIsl = isls.all().betweenSwitches(switchesWithRtl).first()

        when: "Disable portDiscovery on the srcPort"
        roundTripIsl.srcEndpoint.setDiscovery(false)

        then: "Isl is still DISCOVERED"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            def islInfoForward = roundTripIsl.getInfo(links, false)
            def islInfoReverse = roundTripIsl.getInfo(links, true)
            assert islInfoForward.state == DISCOVERED
            assert islInfoForward.actualState == FAILED
            assert islInfoReverse.state == DISCOVERED
            assert islInfoReverse.actualState == DISCOVERED
        }

        when: "Disable portDiscovery property on the dstPort"
        roundTripIsl.dstEndpoint.setDiscovery(false)

        then: "Status of the link is changed to FAILED"
        //don't need to wait discoveryTimeout, disablePortDiscovery(on src/dst sides) == portDown
        Wrappers.wait(WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            def islInfoForward = roundTripIsl.getInfo(allLinks, false)
            def islInfoReverse = roundTripIsl.getInfo(allLinks, true)
            assert islInfoForward.state == FAILED
            assert islInfoForward.actualState == FAILED
            assert islInfoReverse.state == FAILED
            assert islInfoReverse.actualState == FAILED
        }

        when: "Enable portDiscovery on the src/dst ports"
        roundTripIsl.srcEndpoint.setDiscovery(true)
        roundTripIsl.dstEndpoint.setDiscovery(true)

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            def islInfoForward = roundTripIsl.getInfo(links, false)
            def islInfoReverse = roundTripIsl.getInfo(links, true)
            assert islInfoForward.state == DISCOVERED
            assert islInfoForward.actualState == DISCOVERED
            assert islInfoReverse.state == DISCOVERED
            assert islInfoReverse.actualState == DISCOVERED
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Able to delete failed ISL without force if it was discovered with disabled portDiscovery on a switch"() {
        given: "A deleted round trip latency ISL"
        def roundTripIsl = isls.all().betweenSwitches(switchesWithRtl).first()

        roundTripIsl.breakIt()
        Wrappers.wait(WAIT_OFFSET) {
            //https://github.com/telstra/open-kilda/issues/3847
            Wrappers.silent { roundTripIsl.delete() }
            assert !roundTripIsl.isPresent()
        }

        when: "Disable portDiscovery on the srcPort"
        roundTripIsl.srcEndpoint.setDiscovery(false)

        and: "Revive the ISL back (bring switch port up)"
        roundTripIsl.restore()

        then: "The src/dst switches are valid"
        //https://github.com/telstra/open-kilda/issues/3906
//        switchHelper.synchronizeAndGetFixedEntries([roundTripIsl.srcSwitch, roundTripIsl.dstSwitch]).isEmpty()

        when: "Disable portDiscovery on the dstPort"
        roundTripIsl.dstEndpoint.setDiscovery(false)

        then: "The ISL is failed"
        roundTripIsl.waitForStatus(FAILED, WAIT_OFFSET)

        when: "Delete the ISL without the 'force' option"
        roundTripIsl.delete()

        then: "The ISL is deleted"
        Wrappers.wait(WAIT_OFFSET) {
            !roundTripIsl.isPresent()
        }
    }
}
