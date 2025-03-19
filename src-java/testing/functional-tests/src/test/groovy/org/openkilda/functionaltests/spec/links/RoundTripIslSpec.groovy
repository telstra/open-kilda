package org.openkilda.functionaltests.spec.links

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchFeature
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import spock.lang.See

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

    /*we need this variable because it takes more time to DEACTIVATE a switch
    via the 'knockoutSwitch' method on the stage env*/
    Integer customWaitOffset = WAIT_OFFSET * 4


    @Tags(ISL_RECOVER_ON_FAIL)
    def "Isl with round-trip properly changes status after port events(#descr)"() {
        given: "Round-trip ISL with a-switch"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort &&
                [it.srcSwitch, it.dstSwitch].every { it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }
        }
        assumeTrue(isl != null, "Wasn't able to find round-trip ISL with a-switch")
        islHelper.setLinkBfd(isl)

        when: "Port down event happens"
        islHelper.breakIsl(isl)

        and: "Port up event happens, but traffic goes only in one direction"
        aSwitchFlows.removeFlows([isl.aswitch])

        then: "ISL is not getting discovered"
        TimeUnit.SECONDS.sleep(discoveryInterval + 2)
        northbound.getLink(isl).state == FAILED
        northbound.getLink(isl.reversed).state == FAILED

        when: "Traffic starts to flow in both directions"
        aSwitchFlows.addFlows([isl.aswitch])

        then: "ISL gets discovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def fw = northbound.getLink(isl)
            def rv = northbound.getLink(isl.reversed)
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
        def swToDeactivate = switches.all().getListOfSwitches().find { sw ->
            if (sw.getDbFeatures().contains(SwitchFeature.NOVIFLOW_COPY_FIELD)) {
                roundTripIsls = topology.getRelatedIsls(sw.switchId).findAll {
                    it.dstSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
                }
                nonRoundTripIsls = topology.getRelatedIsls(sw.switchId).findAll {
                    !it.dstSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
                }
                roundTripIsls && nonRoundTripIsls
            }
        } ?: assumeTrue(false, "Wasn't able to find a switch with suitable links")

        when: "Simulate connection lose between the switch and FL, the switch becomes DEACTIVATED and remains operable"
        swToDeactivate.knockout(RW)

        and: "Wait discoveryTimeout"
        sleep(discoveryTimeout * 1000)

        then: "All non round trip latency ISLs are FAILED"
        Wrappers.wait(WAIT_OFFSET) {
            withPool {
                nonRoundTripIsls.eachParallel { assert northbound.getLink(it).state == FAILED }
            }
        }

        and: "All round trip latency ISLs are still DISCOVERED (the system uses round trip latency status \
for ISL alive confirmation)"
        withPool {
            roundTripIsls.eachParallel { assert northbound.getLink(it).state == DISCOVERED }
        }
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "A round trip latency ISL goes down when both switches lose connection to FL"() {
        given: "A round trip latency ISL"
        def swPair = switchPairs.all().neighbouring().withIslRttSupport().first()
        Isl roundTripIsl = topology.getIslBetween(swPair.src.sw, swPair.dst.sw).get()
        assert roundTripIsl

        when: "Switches lose connection to FL, switches become DEACTIVATED but keep processing packets"
        swPair.src.knockout(RW)
        swPair.dst.knockout(RW)

        then: "The round trip latency ISL is FAILED (because round_trip_status is not available in DB for current ISL on both switches)"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            assert northbound.getLink(roundTripIsl).state == FAILED
        }
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "A round trip latency ISL goes down when the src switch lose connection to FL and \
round trip latency rule is removed on the dst switch"() {
        given: "A round trip latency ISL"
        def swPair = switchPairs.all().neighbouring().withIslRttSupport().first()
        Isl roundTripIsl = topology.getIslBetween(swPair.src.sw, swPair.dst.sw).get()
        assert roundTripIsl

        and: "Round trip status is ACTIVE for the given ISL in both directions"
        [roundTripIsl, roundTripIsl.reversed].each {
            assert northbound.getLink(it).roundTripStatus == DISCOVERED
        }

        when: "Simulate connection lose between the src switch and FL, switches become DEACTIVATED and remain operable"
        def mgmtBlockData = swPair.src.knockout(RW)

        then: "Round trip status for forward direction is not available and ACTIVE in reverse direction"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            assert northbound.getLink(roundTripIsl).roundTripStatus == FAILED
            assert northbound.getLink(roundTripIsl.reversed).roundTripStatus == DISCOVERED
        }

        when: "Delete ROUND_TRIP_LATENCY_RULE_COOKIE on the dst switch"
        swPair.dst.rulesManager.delete(DeleteRulesAction.REMOVE_ROUND_TRIP_LATENCY)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert swPair.dst.validate().rules.missing.size() == 1
        }

        then: "The round trip latency ISL is FAILED"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            assert northbound.getLink(roundTripIsl).state == FAILED
        }

        and: "Round trip status is not available for the given ISL in both directions"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [roundTripIsl, roundTripIsl.reversed].each {
                assert northbound.getLink(it).roundTripStatus == FAILED
            }
        }

        when: "Restore connection between the src switch and FL"
        swPair.src.revive(mgmtBlockData)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == DISCOVERED
            }.size() == topology.islsForActiveSwitches.size() * 2
        }

        then: "Round trip isl is DISCOVERED"
        northbound.getLink(roundTripIsl).state == DISCOVERED

        and: "Round trip status is available for the given ISL in forward direction only"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(roundTripIsl).roundTripStatus == DISCOVERED
            assert northbound.getLink(roundTripIsl.reversed).roundTripStatus == FAILED
        }

        when: "Install ROUND_TRIP_LATENCY_RULE_COOKIE on the dst switch"
        swPair.dst.rulesManager.install(InstallRulesAction.INSTALL_ROUND_TRIP_LATENCY)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert !swPair.dst.validateAndCollectFoundDiscrepancies().isPresent()
        }

        then: "Round trip status is available for the given ISL in both directions"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [roundTripIsl, roundTripIsl.reversed].each {
                assert northbound.getLink(it).roundTripStatus == DISCOVERED }
        }
    }

    @Tags([SMOKE_SWITCHES])
    def "A round trip latency ISL goes down when portDiscovery property is disabled on the src/dst ports"() {
        given: "A round trip latency ISL"
        def swPair = switchPairs.all().neighbouring().withIslRttSupport().first()
        Isl roundTripIsl = topology.getIslBetween(swPair.src.sw, swPair.dst.sw).get()
        assert roundTripIsl

        when: "Disable portDiscovery on the srcPort"
        def islSrcPort = swPair.src.getPort(roundTripIsl.srcPort)
        islSrcPort.setDiscovery(false)

        then: "Isl is still DISCOVERED"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            def islInfoForward = islUtils.getIslInfo(links, roundTripIsl,).get()
            def islInfoReverse = islUtils.getIslInfo(links, roundTripIsl.reversed).get()
            assert islInfoForward.state == DISCOVERED
            assert islInfoForward.actualState == FAILED
            assert islInfoReverse.state == DISCOVERED
            assert islInfoReverse.actualState == DISCOVERED
        }

        when: "Disable portDiscovery property on the dstPort"
        def islDstPort = swPair.dst.getPort(roundTripIsl.dstPort)
        islDstPort.setDiscovery(false)

        then: "Status of the link is changed to FAILED"
        //don't need to wait discoveryTimeout, disablePortDiscovery(on src/dst sides) == portDown
        Wrappers.wait(WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            def islInfoForward = islUtils.getIslInfo(allLinks, roundTripIsl).get()
            def islInfoReverse = islUtils.getIslInfo(allLinks, roundTripIsl.reversed).get()
            assert islInfoForward.state == FAILED
            assert islInfoForward.actualState == FAILED
            assert islInfoReverse.state == FAILED
            assert islInfoReverse.actualState == FAILED
        }

        when: "Enable portDiscovery on the src/dst ports"
        islSrcPort.setDiscovery(true)
        islDstPort.setDiscovery(true)

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            def islInfoForward = islUtils.getIslInfo(links, roundTripIsl).get()
            def islInfoReverse = islUtils.getIslInfo(links, roundTripIsl.reversed).get()
            assert islInfoForward.state == DISCOVERED
            assert islInfoForward.actualState == DISCOVERED
            assert islInfoReverse.state == DISCOVERED
            assert islInfoReverse.actualState == DISCOVERED
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Able to delete failed ISL without force if it was discovered with disabled portDiscovery on a switch"() {
        given: "A deleted round trip latency ISL"
        def swPair = switchPairs.all().neighbouring().withIslRttSupport().first()
        Isl roundTripIsl = topology.getIslBetween(swPair.src.sw, swPair.dst.sw).get()
        assert roundTripIsl

        islHelper.breakIsl(roundTripIsl)
        Wrappers.wait(WAIT_OFFSET) {
            //https://github.com/telstra/open-kilda/issues/3847
            Wrappers.silent { northbound.deleteLink(islUtils.toLinkParameters(roundTripIsl)) }
            def links = northbound.getAllLinks()
            assert !islUtils.getIslInfo(links, roundTripIsl).present
        }

        when: "Disable portDiscovery on the srcPort"
        def islSrcPort = swPair.src.getPort(roundTripIsl.srcPort)
        islSrcPort.setDiscovery(false)

        and: "Revive the ISL back (bring switch port up)"
        islHelper.restoreIsl(roundTripIsl)

        then: "The src/dst switches are valid"
        //https://github.com/telstra/open-kilda/issues/3906
//        switchHelper.synchronizeAndGetFixedEntries([roundTripIsl.srcSwitch, roundTripIsl.dstSwitch]).isEmpty()

        when: "Disable portDiscovery on the dstPort"
        def islDstPort = swPair.dst.getPort(roundTripIsl.dstPort)
        islDstPort.setDiscovery(false)

        then: "The ISL is failed"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, roundTripIsl).get().state == FAILED
            assert islUtils.getIslInfo(links, roundTripIsl.reversed).get().state == FAILED
        }

        when: "Delete the ISL without the 'force' option"
        islHelper.deleteIsl(roundTripIsl)

        then: "The ISL is deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert !islUtils.getIslInfo(links, roundTripIsl).present
        }
    }
}
