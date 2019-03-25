package org.openkilda.functionaltests.spec.network

import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException

class PathsSpec extends BaseSpecification {

    def "Get paths between not neighboring switches"() {
        given: "Two active not neighboring switches"

        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link ->
                !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId)
            }
        }

        and: "Create a flow to reduce available bandwidth on some path between these two switches"
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        when: "Get paths between switches"
        def paths = northbound.getPaths(srcSwitch.dpId, dstSwitch.dpId)

        then: "Paths will be sorted by bandwidth (descending order) and then by latency (ascending order)"
        paths.paths.size() > 0

        for (int i = 1; i < paths.paths.size(); i++) {
            def prevPath = paths.paths[i - 1]
            def curPath = paths.paths[i]

            assert prevPath.bandwidth >= curPath.bandwidth

            if (prevPath.bandwidth == curPath.bandwidth) {
                assert prevPath.latency <= curPath.latency
            }
        }

        and: "Cleanup: delete flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to get paths between one switch"() {
        given: "An active switch"
        def sw = topology.getActiveSwitches()[0]

        when: "Try to get paths between one switch"
        northbound.getPaths(sw.dpId, sw.dpId)

        then: "Get 400 BadRequest error because request is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
    }

    def "Unable to get paths between nonexistent switch"() {
        given: "An active switch"
        def sw = topology.getActiveSwitches()[0]

        when: "Try to get paths between real switch and nonexistent switch"
        northbound.getPaths(sw.dpId, NON_EXISTENT_SWITCH_ID)

        then: "Get 404 NotFound error"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
    }
}
