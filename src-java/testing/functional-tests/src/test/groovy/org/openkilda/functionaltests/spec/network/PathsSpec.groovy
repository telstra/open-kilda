package org.openkilda.functionaltests.spec.network

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.network.PathDto
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Unroll

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.model.SwitchPairFilter.WITH_AT_LEAST_N_NON_OVERLAPPING_PATHS
import static org.openkilda.functionaltests.helpers.model.SwitchPairFilter.SHORTEST_PATH_IS_SHORTER_THAN_OTHERS
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.FlowEncapsulationType.VXLAN
import static org.openkilda.model.PathComputationStrategy.LATENCY
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.DST_SWITCH
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.FLOW_ENCAPSULATION_TYPE
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.MAX_LATENCY
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.MAX_PATH_COUNT
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.PATH_COMPUTATION_STRATEGY
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.PROTECTED
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.SRC_SWITCH
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.NOT_FOUND

class PathsSpec extends HealthCheckSpecification {

    @Tidy
    @Tags(SMOKE)
    def "Get paths between not neighboring switches"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getNotNeighboringSwitchPair()

        and: "Create a flow to reduce available bandwidth on some path between these two switches"
        def flow = flowHelperV2.addFlow(flowHelperV2.randomFlow(switchPair))

        when: "Get paths between switches"
        def paths = northbound.getPaths("convert switch pair to paths request parameters"(switchPair))

        then: "Paths will be sorted by bandwidth (descending order) and then by latency (ascending order)"
        paths && paths == paths.sort { a, b ->
            def cmp = b.getBandwidth() <=> a.getBandwidth()
            if (cmp != 0) {
                return cmp
            }
            a.getLatencyNs() <=> b.getLatencyNs()
        }

        then: "Maximum count of paths can be changed during PCE calculations"
        def expectedPathsCount = 1
        def limited_paths = northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                [(MAX_PATH_COUNT): expectedPathsCount])
        assert limited_paths.size() == expectedPathsCount
        assert paths.size() > limited_paths.size()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Able to get paths between switches for the LATENCY strategy"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getNotNeighboringSwitchPair()

        and: "Create a flow to reduce available bandwidth on some path between these two switches"
        def flow = flowHelperV2.addFlow(flowHelperV2.randomFlow(switchPair))

        when: "Get paths between switches using the LATENCY strategy"
        def paths = northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                [(PATH_COMPUTATION_STRATEGY): LATENCY])

        then: "Paths will be sorted by latency (ascending order) and then by bandwidth (descending order)"
        paths && paths == paths.sort { a, b ->
            def cmp = a.getLatencyNs() <=> b.getLatencyNs()
            if (cmp != 0) {
                return cmp
            }
            b.getBandwidth() <=> a.getBandwidth()
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Unable to get paths between #problemDescription"() {
        when: "Try to get paths between #problemDescription"
        northbound.getPaths([(SRC_SWITCH): srcSwitchId, (DST_SWITCH): dstSwitchId])

        then:
        "Get ${expectedStatus.toString()} error because request is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == expectedStatus

        where:
        problemDescription    | srcSwitchId                              | dstSwitchId                              | expectedStatus
        "one switch"          | topology.getSwitches().first().getDpId() | topology.getSwitches().first().getDpId() | BAD_REQUEST
        "non-existing switch" | topology.getSwitches().first().getDpId() | NON_EXISTENT_SWITCH_ID                   | NOT_FOUND
    }

    @Tidy
    def "Unable to get paths with max_latency strategy without max latency parameter"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getNotNeighboringSwitchPair()

        when: "Try to get paths between switches with max_latency stragy but without max_latency parameter"
        northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                [(PATH_COMPUTATION_STRATEGY): MAX_LATENCY])

        then: "Human readable error is returned"
        def error = thrown(HttpClientErrorException)
        error.statusCode == BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Missed max_latency parameter."
        errorDetails.errorDescription == "MAX_LATENCY path computation strategy requires non null max_latency " +
                "parameter. If max_latency will be equal to 0 LATENCY strategy will be used instead of MAX_LATENCY."
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Unable to get a path for a 'vxlan' flowEncapsulationType when switches do not support it"() {
        given: "Two active not supported 'vxlan' flowEncapsulationType switches"
        def switchPair = topologyHelper.switchPairs.first()
        Map<Switch, SwitchPropertiesDto> initProps = [switchPair.src, switchPair.dst].collectEntries {
            [(it): switchHelper.getCachedSwProps(it.dpId)]
        }
        initProps.each { sw, swProp ->
            switchHelper.updateSwitchProperties(sw, swProp.jacksonCopy().tap {
                it.supportedTransitEncapsulation = [TRANSIT_VLAN.toString()]
            })
        }
        assumeTrue(switchPair as boolean, "Unable to find required switches in topology")
        def encapsTypesWithoutVxlan = northbound.getSwitchProperties(switchPair.src.dpId)
                .supportedTransitEncapsulation.collect { it.toString().toUpperCase() }

        when: "Try to get a path for a 'vxlan' flowEncapsulationType between the given switches"
        northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                [(FLOW_ENCAPSULATION_TYPE): VXLAN])

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Switch $switchPair.src.dpId doesn't support $VXLAN " +
                "encapsulation type. Choose one of the supported encapsulation types $encapsTypesWithoutVxlan or " +
                "update switch properties and add needed encapsulation type."

        cleanup:
        initProps.each { sw, swProps ->
            switchHelper.updateSwitchProperties(sw, swProps)
        }
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    @Unroll
    def "Protected path is #isIncludedString included into path list if #isIncludedString requested"() {
        given: "Two switches with potential protected path"
        def switchPair = topologyHelper.allNotNeighboringSwitchPairs
                .findAll { WITH_AT_LEAST_N_NON_OVERLAPPING_PATHS(it, 2) }
                .shuffled()
                .first()

        when: "Request available protected paths between two switches"
        def paths = northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                [(PROTECTED): isIncluded])

        then: "All paths #isIncludedString have protected ones and protected path differs from main path"
        paths.findAll(verificationFilter).isEmpty()

        where:
        isIncludedString | isIncluded | verificationFilter
        ""               | true       | { !pathHelper."paths can be mutually protected"(it.getNodes(), it.getProtectedPath().getNodes()) }
        "not"            | false      | { it.getProtectedPath() != null }
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Protected path is null if it doesn't match criteria"() {
        given: "Two non-neighbouring switches with the one path shorter than others"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs()
                .sort {it.paths.sort {it.size()}}
                .findAll (SHORTEST_PATH_IS_SHORTER_THAN_OTHERS).first()

        when: "Request available protected paths matching exactly one path"
        def paths = []
        def lowestLatency = 0
        Wrappers.wait(WAIT_OFFSET) {
            lowestLatency = northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                    [(PATH_COMPUTATION_STRATEGY): LATENCY])
                    .first().getLatencyMs() + 1
            paths = northbound.getPaths("convert switch pair to paths request parameters"(switchPair) +
                    [(PATH_COMPUTATION_STRATEGY): PathComputationStrategy.MAX_LATENCY,
                     (MAX_LATENCY)              : lowestLatency,
                     (PROTECTED)                : true])
            assert paths.size() > 0, "Test was unable to request for paths while latency values weren't updated"
        }

        then: "The path has no protected one if potential protected paths don't match latency criteria below ${lowestLatency}"
        paths.findAll {"protected path exists but has latency above limit or common ISLs with main path"(it, lowestLatency)}.isEmpty()
    }

    def "convert switch pair to paths request parameters"(SwitchPair switchPair) {
        return [(SRC_SWITCH): switchPair.getSrc().getDpId(),
                (DST_SWITCH): switchPair.getDst().getDpId()]
    }

    def "protected path exists but has latency above limit or common ISLs with main path" (PathDto path, Long latencyLimit) {
        PathDto protectedPath = path.getProtectedPath()
        return protectedPath != null &&
                (protectedPath.getLatencyMs() > latencyLimit || pathHelper."paths can be mutually protected"(path.getNodes(), protectedPath.getNodes()))
    }
}
