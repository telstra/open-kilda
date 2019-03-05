package org.openkilda.functionaltests.spec.links

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

import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Narrative("Verify scenarios around replugging ISLs between different switches/ports.")
class IslReplugSpec extends BaseSpecification {

    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    def "ISL status changes to MOVED when replugging ISL into another switch"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find { it.getAswitch()?.inPort && it.getAswitch()?.outPort }
        assumeTrue("Wasn't able to find enough of required a-switch links", isl.asBoolean())

        and: "A non-connected a-switch link"
        def notConnectedIsl = topology.notConnectedIsls.find {
            it.srcSwitch != isl.srcSwitch && it.srcSwitch != isl.dstSwitch
        }
        assumeTrue("Wasn't able to find enough of required a-switch links", notConnectedIsl.asBoolean())

        when: "Replug one end of the connected link to the not connected one"
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true)

        then: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)

        and: "New ISL becomes DISCOVERED"
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], DISCOVERED)

        when: "Replug the link back where it was"
        islUtils.replug(newIsl, true, isl, false)

        then: "Original ISL becomes DISCOVERED again"
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)

        and: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)

        and: "MOVED ISL can be deleted"
        [newIsl, newIsl.reversed].each { Isl islToRemove ->
            assert northbound.deleteLink(islUtils.toLinkParameters(islToRemove)).deleted
            assert Wrappers.wait(WAIT_OFFSET) { !islUtils.getIslInfo(islToRemove).isPresent() }

        }
    }

    def "New potential self-loop ISL (the same port on the same switch) is not getting discovered when replugging"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find { it.getAswitch()?.inPort && it.getAswitch()?.outPort }
        assumeTrue("Wasn't able to find enough of required a-switch links", isl.asBoolean())

        when: "Replug one end of the link into 'itself'"
        def loopedIsl = islUtils.replug(isl, false, isl, true)

        then: "Replugged ISL status changes to FAILED"
        islUtils.waitForIslStatus([isl, isl.reversed], FAILED)

        and: "The potential self-loop ISL is not present in the list of ISLs"
        def allLinks = northbound.getAllLinks()
        !islUtils.getIslInfo(allLinks, loopedIsl).present
        !islUtils.getIslInfo(allLinks, loopedIsl.reversed).present

        when: "Replug the link back where it was"
        islUtils.replug(loopedIsl, true, isl, false)

        then: "Original ISL becomes DISCOVERED again"
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
    }

    def "New potential self-loop ISL (different ports on the same switch) is not getting discovered when replugging"() {
        given: "Two a-switch links on a single switch"
        def isls = topology.isls.findAll { it.aswitch && it.srcSwitch?.active }
                .inject([:].withDefault { [] }) { r, link ->
            link.srcSwitch && r[link.srcSwitch] << link
            link.dstSwitch && r[link.dstSwitch] << link.reversed
            r //map where key: switch, value: list of a-switch isls related to this switch
        }.find { k, v ->
            k.ofVersion != "OF_12" &&
                    v.findAll { !it.dstSwitch }.size() > 1 //contains at least 2 not connected asw link
        }?.value as List<TopologyDefinition.Isl>
        assumeNotNull("Not able to find required switch with enough free a-switch ISLs", isls)

        def notConnectedIsls = isls.findAll { !it.dstSwitch }
        def islToPlug = notConnectedIsls[0]
        def islToPlugInto = notConnectedIsls[1]

        when: "Plug an ISL between two ports on the same switch"
        def beforeReplugTime = new Date()
        def dropCounterBefore = northbound.getSwitchRules(islToPlugInto.srcSwitch.dpId).flowEntries.find {
            it.cookie == DefaultRule.DROP_LOOP_RULE.cookie
        }.packetCount
        def expectedIsl = islUtils.replug(islToPlug, true, islToPlugInto, true)

        then: "The potential self-loop ISL is not present in the list of ISLs (wait for discovery interval)"
        TimeUnit.SECONDS.sleep(discoveryInterval + WAIT_OFFSET)
        def allLinks = northbound.getAllLinks()
        !islUtils.getIslInfo(allLinks, expectedIsl).present
        !islUtils.getIslInfo(allLinks, expectedIsl.reversed).present

        and: "Self-loop rule packet counter is incremented and logged in otsdb"
        def statsData = null
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
            statsData = otsdb.query(beforeReplugTime, metricPrefix + "switch.flow.system.packets",
                    [switchid : expectedIsl.srcSwitch.dpId.toOtsdFormat(),
                     cookieHex: DefaultRule.DROP_LOOP_RULE.toHexString()]).dps
            assert statsData && !statsData.empty
        }
        statsData.values().last().toLong() > dropCounterBefore

        and: "Unplug the link how it was before"
        lockKeeper.removeFlows([expectedIsl.aswitch, expectedIsl.aswitch.reversed])
    }
}
