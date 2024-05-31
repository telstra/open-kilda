package org.openkilda.functionaltests.spec.network

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.web.client.HttpClientErrorException

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.FlowEncapsulationType.VXLAN
import static org.openkilda.model.PathComputationStrategy.LATENCY
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.FLOW_ENCAPSULATION_TYPE
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.MAX_LATENCY
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.MAX_PATH_COUNT
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.PATH_COMPUTATION_STRATEGY
import static org.openkilda.testing.service.northbound.payloads.PathRequestParameter.PROTECTED
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.NOT_FOUND

class PathsSpec extends HealthCheckSpecification {

    @Tags(SMOKE)
    def "Get paths between not neighboring switches"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .random()

        and: "Create a flow to reduce available bandwidth on some path between these two switches"
        def flow = flowHelperV2.addFlow(flowHelperV2.randomFlow(switchPair))

        when: "Get paths between switches"
        def paths = switchPair.getPathsFromApi()

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
        def limited_paths = switchPair.getPathsFromApi([(MAX_PATH_COUNT): expectedPathsCount])
        assert limited_paths.size() == expectedPathsCount
        assert paths.size() > limited_paths.size()
    }

    @Tags(LOW_PRIORITY)
    def "Able to get paths between switches for the LATENCY strategy"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .random()

        and: "Create a flow to reduce available bandwidth on some path between these two switches"
        def flow = flowHelperV2.addFlow(flowHelperV2.randomFlow(switchPair))

        when: "Get paths between switches using the LATENCY strategy"
        def paths = switchPair.getPathsFromApi([(PATH_COMPUTATION_STRATEGY): LATENCY])

        then: "Paths will be sorted by latency (ascending order) and then by bandwidth (descending order)"
        paths && paths == paths.sort { a, b ->
            def cmp = a.getLatencyNs() <=> b.getLatencyNs()
            if (cmp != 0) {
                return cmp
            }
            b.getBandwidth() <=> a.getBandwidth()
        }
    }

    @Tags(LOW_PRIORITY)
    def "Unable to get paths for #problemDescription"() {
        when: "Try to get paths between #problemDescription"
        switchPair(topology.getSwitches().first(), northbound).getPathsFromApi()


        then:
        "Get error because request is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == expectedStatus

        where:
        problemDescription | switchPair |expectedStatus
        "one switch"              | { Switch sw, NorthboundService nb -> SwitchPair.singleSwitchInstance(sw, nb)}| BAD_REQUEST
        "non-existing switch" |{ Switch sw, NorthboundService nb -> SwitchPair.withNonExistingDstSwitch(sw, nb)} | NOT_FOUND
    }

    def "Unable to get paths with max_latency strategy without max latency parameter"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all()
        .nonNeighbouring()
        .random()

        when: "Try to get paths between switches with max_latency strategy but without max_latency parameter"
        switchPair.getPathsFromApi([(PATH_COMPUTATION_STRATEGY): MAX_LATENCY])

        then: "Human readable error is returned"
        def error = thrown(HttpClientErrorException)
        error.statusCode == BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Missed max_latency parameter."
        errorDetails.errorDescription == "MAX_LATENCY path computation strategy requires non null max_latency " +
                "parameter. If max_latency will be equal to 0 LATENCY strategy will be used instead of MAX_LATENCY."
    }

    @Tags(LOW_PRIORITY)
    def "Unable to get a path for a 'vxlan' flowEncapsulationType when switches do not support it"() {
        given: "Two active not supported 'vxlan' flowEncapsulationType switches"
        def switchPair = switchPairs.all().random()
        Map<Switch, SwitchPropertiesDto> initProps = [switchPair.src, switchPair.dst].collectEntries {
            [(it): switchHelper.getCachedSwProps(it.dpId)]
        }
        initProps.each { sw, swProp ->
            switchHelper.updateSwitchProperties(sw, swProp.jacksonCopy().tap {
                it.supportedTransitEncapsulation = [TRANSIT_VLAN.toString()]
            })
        }
        def encapsTypesWithoutVxlan = northbound.getSwitchProperties(switchPair.src.dpId)
                .supportedTransitEncapsulation.collect { it.toString().toUpperCase() }

        when: "Try to get a path for a 'vxlan' flowEncapsulationType between the given switches"
        switchPair.getPathsFromApi([(FLOW_ENCAPSULATION_TYPE): VXLAN])

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Switch $switchPair.src.dpId doesn't support $VXLAN " +
                "encapsulation type. Choose one of the supported encapsulation types $encapsTypesWithoutVxlan or " +
                "update switch properties and add needed encapsulation type."
    }

    @Tags(LOW_PRIORITY)
    def "Protected path is #isIncludedString included into path list if #isIncludedString requested"() {
        given: "Two switches with potential protected path"
        def switchPair = switchPairs.all()
                .withAtLeastNNonOverlappingPaths(2)
                .random()

        when: "Request available protected paths between two switches"
        def paths = switchPair.getPathsFromApi([(PROTECTED): isIncluded])

        then: "All paths #isIncludedString have protected ones and protected path differs from main path"
        paths.findAll(verificationFilter).isEmpty()

        where:
        isIncludedString | isIncluded | verificationFilter
        ""               | true       | { !it.canBeProtectedFor(it.getProtectedPath()) }
        "not"            | false      | { it.getProtectedPath() != null }
    }

    @Tags(LOW_PRIORITY)
    def "Protected path is null if it doesn't match criteria"() {
        given: "Two non-neighbouring switches with the one path shorter than others"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withShortestPathShorterThanOthers()
                .sortedByShortestPathLengthAscending()
                .first()

        when: "Request available protected paths matching exactly one path"
        def paths = []
        def lowestLatency = 0
        Wrappers.wait(WAIT_OFFSET) {
            lowestLatency = switchPair.getPathsFromApi([(PATH_COMPUTATION_STRATEGY): LATENCY]).first().getLatencyMs() + 1
            paths = switchPair.getPathsFromApi([(PATH_COMPUTATION_STRATEGY): PathComputationStrategy.MAX_LATENCY,
                     (MAX_LATENCY)              : lowestLatency,
                     (PROTECTED)                : true])
            assert paths.size() > 0, "Test was unable to request for paths while latency values weren't updated"
        }

        then:
        "The path has no protected one if potential protected paths don't match latency criteria below ${lowestLatency}"
        paths.findAll { it.hasProtectedPathWithLatencyAbove(lowestLatency) }.isEmpty()
    }
}