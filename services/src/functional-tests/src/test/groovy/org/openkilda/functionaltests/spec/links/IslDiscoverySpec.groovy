package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType

import spock.lang.Ignore

class IslDiscoverySpec extends HealthCheckSpecification {

    @Ignore("under development")
    def "rename: discover isl on a customer port"() {
        setup: "Find a switch with at least 2 isls"
        //first isl will be removed in order to rediscover it on a customer port afterwards
        //second isl is just required to build a common flow between 2 switches
        def swPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find {
            topology.getBusyPortsForSwitch(it.src).size() > 1
        }
        def sw = swPair.src
        def swIsls = topology.getRelatedIsls(sw)

        and: "Disable one of the isl ports and delete the isl"
        def pathIsls = swPair.paths.collectMany { pathHelper.getInvolvedIsls(it) }.unique().collect { [it, it.reversed] }
        //prefer an isl which is not used in any of the paths between switches in order to prevent an 'island' situation
        def targetIsl = swIsls.find { !pathIsls.contains(it) } ?: swIsls.first()
        antiflap.portDown(sw.dpId, targetIsl.srcPort)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            assert northbound.getLink(targetIsl).state == IslChangeType.FAILED
        }
        northbound.deleteLink(islUtils.toLinkParameters(targetIsl))

        and: "Create a flow on the port where isl used to be"
        def flow = flowHelperV2.randomFlow(swPair)
        flow.source.portNumber = targetIsl.srcPort
        flowHelperV2.addFlow(flow)

        when: "Isl port is brought up and flow port starts to receive discovery packets"
        antiflap.portUp(sw.dpId, targetIsl.srcPort)

        then: "Isl is discovered on a flow port"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(targetIsl).state == IslChangeType.DISCOVERED
        }
        targetIsl.srcPort == flow.source.portNumber
    }

    @Ignore("not ready yet")
    def "tbd"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find { it.getAswitch()?.inPort && it.getAswitch()?.outPort }
        assumeTrue("Can't find connected a-switch link", isl.asBoolean())

        and: "A non-connected a-switch link with a flow on the src port"
        def notConnectedIsl = topology.notConnectedIsls.find {
            it.srcSwitch != isl.srcSwitch && it.srcSwitch != isl.dstSwitch
        }
        assumeTrue("Can't find non-connected a-switch link", notConnectedIsl.asBoolean())
        def flow = flowHelper.singleSwitchFlow(notConnectedIsl.srcSwitch)
        flow.source.portNumber = notConnectedIsl.srcPort
        flowHelper.addFlow(flow)

        when: "Replug one end of the connected link to the not connected one"
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true)

        then: "???"
        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], DISCOVERED)
        assert northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "Cleanup: Restore system"
        // Replug the link back where it was
        islUtils.replug(newIsl, true, isl, false)
        // Original ISL becomes DISCOVERED again
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        // delete MOVED ISL
        assert northbound.deleteLink(islUtils.toLinkParameters(newIsl)).size() == 2
        Wrappers.wait(WAIT_OFFSET) {
            assert !islUtils.getIslInfo(newIsl).isPresent()
            assert !islUtils.getIslInfo(newIsl.reversed).isPresent()
        }
    }
}
