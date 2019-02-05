package org.openkilda.functionaltests.spec.isl

import static org.junit.Assume.assumeNotNull
import static org.junit.Assume.assumeTrue
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.testing.Constants.DefaultRule
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow

import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Narrative("Verify scenarios around replugging ISLs between different switches/ports.")
class IslMovementSpec extends BaseSpecification {

    def "ISL status changes to MOVED when replugging"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find { it.getAswitch()?.inPort && it.getAswitch()?.outPort }
        assumeTrue("Wasn't able to find enough of required a-switch links", isl.asBoolean())

        and: "A non-connected a-switch link"
        def notConnectedIsl = topology.notConnectedIsls.find {
            it.srcSwitch != isl.srcSwitch && it.srcSwitch != isl.dstSwitch
        }
        assumeTrue("Wasn't able to find enough of required a-switch links", notConnectedIsl.asBoolean())

        when: "Replug one end of connected link to the not connected one"
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true)

        then: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], MOVED)

        and: "New ISL becomes Discovered"
        islUtils.waitForIslStatus([newIsl, islUtils.reverseIsl(newIsl)], DISCOVERED)

        when: "Replug the link back where it was"
        islUtils.replug(newIsl, true, isl, false)

        then: "Original ISL becomes Discovered again"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], DISCOVERED)

        and: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([newIsl, islUtils.reverseIsl(newIsl)], MOVED)
        
        and: "MOVED ISL can be deleted"
        [newIsl, islUtils.reverseIsl(newIsl)].each { Isl islToRemove ->
            assert northbound.deleteLink(islUtils.getLinkParameters(islToRemove)).deleted
            assert Wrappers.wait(WAIT_OFFSET) { !islUtils.getIslInfo(islToRemove).isPresent() }

        }
    }

    def "New ISL is not getting discovered when replugging into a self-loop (same port)"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find { it.getAswitch()?.inPort && it.getAswitch()?.outPort }
        assumeTrue("Wasn't able to find enough of required a-switch links", isl.asBoolean())

        when: "Replug one end of link into 'itself'"
        def loopedIsl = islUtils.replug(isl, false, isl, true)

        then: "Replugged ISL status changes to FAILED"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], FAILED)

        and: "The potential self-loop ISL is not present in list of isls"
        def allLinks = northbound.getAllLinks()
        !islUtils.getIslInfo(allLinks, loopedIsl).present
        !islUtils.getIslInfo(allLinks, islUtils.reverseIsl(loopedIsl)).present

        when: "Replug the link back where it was"
        islUtils.replug(loopedIsl, true, isl, false)

        then: "Original ISL becomes Discovered again"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], DISCOVERED)
    }

    def "New ISL is not getting discovered when adding new self-loop ISL (different port)"() {
        given: "2 a-switch links on a single switch"
        def isls = topology.isls.findAll { it.aswitch && it.srcSwitch?.active }
                .inject([:].withDefault { [] }) { r, link ->
            link.srcSwitch && r[link.srcSwitch] << link
            link.dstSwitch && r[link.dstSwitch] << islUtils.reverseIsl(link)
            r //map where key: switch, value: list of a-switch isls related to this switch
        }.find { k, v ->
            k.ofVersion != "OF_12" &&
            v.findAll { !it.dstSwitch }.size() > 1 //contains at least 2 not connected asw link
        }?.value as List<TopologyDefinition.Isl>
        assumeNotNull("Not able to find required switch with enough free A-switch ISLs", isls)
        def notConnectedIsls = isls.findAll { !it.dstSwitch }
        def islToPlug = notConnectedIsls[0]
        def islToPlugInto = notConnectedIsls[1]

        when: "Plug an ISL between two ports on the same switch"
        def beforeReplugTime = new Date()
        def dropCounterBefore = northbound.getSwitchRules(islToPlugInto.srcSwitch.dpId).flowEntries.find {
            it.cookie == DefaultRule.DROP_LOOP_RULE.cookie
        }.packetCount
        def expectedIsl = islUtils.replug(islToPlug, true, islToPlugInto, true)

        then: "The potential self-loop ISL is not present in list of isls (wait for discovery interval)"
        TimeUnit.SECONDS.sleep(discoveryInterval + WAIT_OFFSET)
        def allLinks = northbound.getAllLinks()
        !islUtils.getIslInfo(allLinks, expectedIsl).present
        !islUtils.getIslInfo(allLinks, islUtils.reverseIsl(expectedIsl)).present

        and: "Self-loop rule packet counter is incremented and logged in otsdb"
        def statsData = null
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
            statsData = otsdb.query(beforeReplugTime, "pen.switch.flow.system.packets",
                    [switchid: expectedIsl.srcSwitch.dpId.toOtsdFormat(),
                     cookieHex: DefaultRule.DROP_LOOP_RULE.toHexString()]).dps
            assert statsData && !statsData.empty
        }
        statsData.values().last().toLong() > dropCounterBefore

        and: "Unplug the link how it was before"
        lockKeeper.removeFlows([
                new ASwitchFlow(expectedIsl.aswitch.getInPort(), expectedIsl.aswitch.getOutPort()),
                new ASwitchFlow(expectedIsl.aswitch.getOutPort(), expectedIsl.aswitch.getInPort())])
    }
}
