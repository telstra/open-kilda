package org.openkilda.functionaltests.spec.links

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
}
