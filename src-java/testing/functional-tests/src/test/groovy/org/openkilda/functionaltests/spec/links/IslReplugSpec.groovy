package org.openkilda.functionaltests.spec.links

import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.IslExtended
import org.openkilda.functionaltests.model.stats.SwitchStats
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.FLOW_SYSTEM_PACKETS
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.DefaultRule.DROP_LOOP_RULE
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Narrative("Verify scenarios around replugging ISLs between different switches/ports.")
@Tags([TOPOLOGY_DEPENDENT])
class IslReplugSpec extends HealthCheckSpecification {

    @Autowired @Shared
    SwitchStats switchStats

    @Tags(HARDWARE)
    def "Round-trip ISL status changes to MOVED when replugging it into another switch"() {
        given: "A connected a-switch link, round-trip-enabled"
        and: "A non-connected a-switch link with round-trip support"
        def rtlSupportedSws = switches.all().withRtlSupport().getListOfSwitches().switchId
        assumeTrue(rtlSupportedSws.size() > 1, "The current topology doesn't have enough Rtl-supported switches")

        def (IslExtended isl, IslExtended notConnectedIsl) = isls.all().withASwitch().getListOfIsls()
                .findAll { rtlSupportedSws.containsAll(it.involvedSwIds) }
                .findResult { fwIsl ->
            [fwIsl, fwIsl.reversed].findResult { fwOrReversedIsl ->
                def potentialNotConnected = isls.allNotConnected().getListOfIsls()
                        .find { it.srcSw != fwOrReversedIsl.srcSw && rtlSupportedSws.contains(it.srcSwId) }
                potentialNotConnected ? [fwOrReversedIsl, potentialNotConnected] : null
            }
        } ?: [null, null]
        assumeTrue(isl.asBoolean(), "Wasn't able to find enough of required a-switch links with round-trip")
        assumeTrue(notConnectedIsl.asBoolean(),
"Wasn't able to find enough not connected a-switch links with round-trip")

        when: "Replug one end of the connected link to the not connected one"
        def newIsl = isl.replugDestination(notConnectedIsl, true, false)

        then: "Replugged ISL status changes to MOVED"
        isl.waitForStatus(MOVED)

        and: "Round trip status is not ACTIVE for the 'moved' ISL in both directions"
        //https://github.com/telstra/open-kilda/issues/4231
//        [isl, isl.reversed].each {
//            assert northbound.getLink(it).roundTripStatus != DISCOVERED
//        }

        and: "New ISL becomes DISCOVERED"
        newIsl.waitForStatus(DISCOVERED)

        when: "Replug the link back where it was"
        newIsl.replug(true, isl, false, true)

        then: "Original ISL becomes DISCOVERED again"
        isl.waitForStatus(DISCOVERED)

        and: "Replugged ISL status changes to MOVED"
        newIsl.waitForStatus(MOVED)

        when: "Remove the MOVED ISL"
        assert newIsl.delete().size() == 2

        then: "Moved ISL is removed"
        !newIsl.isPresent()
        isl.isPresent()

        and: "The src and dst switches of the isl pass switch validation"
        def involvedSws = switches.all().findSpecific([isl.srcSwId, isl.dstSwId, notConnectedIsl.srcSwId])
        synchronizeAndCollectFixedDiscrepancies(involvedSws).isEmpty()
    }

    def "ISL status changes to MOVED when replugging ISL into another switch"() {
        given: "A connected a-switch link"
        def isl = isls.all().withASwitch().random()

        and: "A non-connected a-switch link"
        def notConnectedIsl = isls.allNotConnected().getListOfIsls().find {
            it.srcSwId !in isl.involvedSwIds
        }
        assumeTrue(notConnectedIsl.asBoolean(), "Wasn't able to find enough of required a-switch links")

        when: "Replug one end of the connected link to the not connected one"
        def newIsl = isl.replugDestination(notConnectedIsl, true, true)

        then: "Replugged ISL status changes to MOVED"
        isl.waitForStatus(MOVED)

        and: "New ISL becomes DISCOVERED"
        newIsl.waitForStatus(DISCOVERED)

        when: "Replug the link back where it was"
        newIsl.replug(true, isl, false, false)

        then: "Original ISL becomes DISCOVERED again"
        isl.waitForStatus(DISCOVERED)

        and: "Replugged ISL status changes to MOVED"
        newIsl.waitForStatus(MOVED)

        when: "Remove the MOVED ISL"
        assert newIsl.delete().size() == 2

        then: "Moved ISL is removed"
        !newIsl.isPresent()
        isl.isPresent()

        and: "The src and dst switches of the isl pass switch validation"
        /* Need wait because of parallel s42 tests. Sw validation may show a missing s42 rule. Just wait for it.
        If problem persists, add a 'read' resource lock for s42_toggle */
        def involvedSws = switches.all().findSpecific([isl.srcSwId, isl.dstSwId, notConnectedIsl.srcSwId])
        Wrappers.wait(WAIT_OFFSET) {
            validateAndCollectFoundDiscrepancies(involvedSws).isEmpty()
        }
    }

    def "New potential self-loop ISL (the same port on the same switch) is not getting discovered when replugging"() {
        given: "A connected a-switch link"
        def isl = isls.all().withASwitch().random()

        when: "Replug one end of the link into 'itself'"
        def loopedIsl = isl.replug(false, isl, true, true)

        then: "Replugged ISL status changes to FAILED"
        isl.waitForStatus(FAILED, discoveryTimeout + WAIT_OFFSET)

        and: "The potential self-loop ISL is not present in the list of ISLs"
        !loopedIsl.isPresent()

        when: "Replug the link back where it was"
        loopedIsl.replug( true, isl, false, false)

        then: "Original ISL becomes DISCOVERED again"
        isl.waitForStatus(DISCOVERED)
        def originIslIsUp = true

        cleanup:
        if (!originIslIsUp) {
            loopedIsl.replug( true, isl, false, false)
            isl.waitForStatus(DISCOVERED)
        }
        isls.all().resetCostsInDb()
    }

    @Tags(SMOKE)
    @Ignore("https://github.com/telstra/open-kilda/issues/3633")
    def "New potential self-loop ISL (different ports on the same switch) is not getting discovered when replugging"() {
        given: "Two a-switch links on a single switch"
        List<IslExtended> aSwIsls = []
        def sw = switches.all().notOF12Version().find { swExtended ->
            aSwIsls = isls.all().relatedTo(swExtended).withASwitch().getListOfIsls()
            aSwIsls.size() >= 2
        }
        assumeTrue(sw.asBoolean(), "Not able to find required switch with enough number of a-switch ISLs")

        def islToPlug = aSwIsls[0]
        def islToPlugInto = aSwIsls[1]

        when: "Plug an ISL between two ports on the same switch"
        def beforeReplugTime = new Date()
        def expectedIsl = islToPlug.replug(false, islToPlugInto, true, true)

        then: "The potential self-loop ISL is not present in the list of ISLs (wait for discovery interval)"
        sleep(discoveryInterval * 1000)
        Wrappers.wait(WAIT_OFFSET) { !expectedIsl.isPresent() }

        and: "Self-loop rule packet counter is incremented and logged in tsdb"
        Wrappers.wait(statsRouterRequestInterval) {
            switchStats.of(expectedIsl.srcSwId)
                    .get(FLOW_SYSTEM_PACKETS, DROP_LOOP_RULE.toHexString())
                    .hasNonZeroValuesAfter(beforeReplugTime.getTime())
        }

        cleanup:
        expectedIsl && lockKeeper.removeFlows([expectedIsl.getASwitch(), expectedIsl.getASwitch().reversed])
        if(islToPlug) {
            def connectedIsls = [islToPlug, islToPlugInto].findAll { it.getASwitch()?.outPort }
            lockKeeper.addFlows(connectedIsls.collectMany { [it.getASwitch(), it.getASwitch().reversed] })
            connectedIsls.each { it.waitForStatus(DISCOVERED) }
        }

        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert links.findAll { it.state != DISCOVERED }.isEmpty()
            assert links.size() == topology.islsForActiveSwitches.size() * 2
        }
        isls.all().resetCostsInDb()
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/5099")
    def "User is able to replug ISL with enabled BFD, receive new ISL, enable bfd on it and replug back"() {
        given: "An ISL with BFD and ability to replug"
        def bfdSwIds = switches.all().bfdSupported().switchId
        def isl = isls.all().withASwitch().getListOfIsls().find {
            bfdSwIds.containsAll(it.involvedSwIds)
        }
        assumeTrue(isl as boolean, "Require at least one BFD ISL")
        def notConnectedIsl = isls.allNotConnected().getListOfIsls().
                find { it.srcSwId != isl.dstSwId && it.srcSwId in bfdSwIds }
        assumeTrue(notConnectedIsl as boolean, "Require at least one 'not connected' ISL")

        isl.setBfd()

        Wrappers.wait(WAIT_OFFSET) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    it.enableBfd
                    it.bfdSessionStatus == "up"
                }
            }
        }

        when: "Replug a bfd-enabled link to some other free port"
        def newIsl = isl.replug(true, notConnectedIsl, true, true)

        then: "Old ISL becomes Moved, new ISL is discovered"
        isl.waitForStatus(MOVED)
        newIsl.waitForStatus(DISCOVERED)

        and: "Bfd on Moved ISL reports 'down' status"
        [isl, isl.reversed].each {
            verifyAll(it.getNbDetails()) {
                enableBfd
                bfdSessionStatus == "down"
            }
        }

        when: "Turn on BFD for new ISL"
        newIsl.setBfd()

        then: "BFD is turned on according to getLink API"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [newIsl, newIsl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    it.enableBfd
                    it.bfdSessionStatus == "up"
                }
            }
        }

        when: "Replug a new link back where it was before"
        def newOldIsl = newIsl.replug(true, isl, true, true)
        assert newOldIsl == isl

        then: "Initial ISL becomes Discovered again, replugged ISL becomes Moved"
        newIsl.waitForStatus(MOVED)
        newOldIsl.waitForStatus(DISCOVERED)


        and: "Bfd on Moved ISL reports 'down' status"
        [newIsl, newIsl.reversed].each {
            verifyAll(it.getNbDetails()) {
                enableBfd
                bfdSessionStatus == "down"
            }
        }

        and: "Bfd on Discovered ISL reports 'up' status"
        [newOldIsl, newOldIsl.reversed].each {
            verifyAll(it.getNbDetails()) {
                enableBfd
                bfdSessionStatus == "up"
            }
        }
    }
}
