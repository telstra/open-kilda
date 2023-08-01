package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify reroute operations on HA-flows.")
class HaFlowRerouteSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid HA-flow can be rerouted"() {
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        given: "An HA-flow"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        def oldPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def islToFail = pathHelper.getInvolvedIsls(PathHelper.convert(oldPaths.subFlowPaths[0].forward)).first()

        when: "Fail an HA-flow ISL (bring switch port down)"
        antiflap.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)
        wait(WAIT_OFFSET) { northbound.getLink(islToFail).state == FAILED }

        then: "The HA-flow was rerouted after reroute delay"
        def newPaths = null
        wait(rerouteDelay + WAIT_OFFSET) {
            haFlowHelper.assertHaFlowAndSubFlowStatuses(haFlow.haFlowId, FlowState.UP)
            newPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
            newPaths != oldPaths
        }
        newPaths != null

        and: "History has relevant entries about HA-flow reroute"
        // TODO when https://github.com/telstra/open-kilda/issues/5169 will be closed

        and: "HA-flow passes validation"
        northboundV2.validateHaFlow(haFlow.haFlowId).asExpected

        and: "All involved switches pass switch validation"
        def allInvolvedSwitchIds = haFlowHelper.getInvolvedSwitches(oldPaths) + haFlowHelper.getInvolvedSwitches(newPaths)
        withPool {
            allInvolvedSwitchIds.eachParallel { SwitchId switchId ->
                northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        islToFail && antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        wait(WAIT_OFFSET) { northbound.getLink(islToFail).state == DISCOVERED }
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Tags(SMOKE)
    def "HA-flow in 'Down' status is rerouted when discovering a new ISL"() {
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        given: "An HA-flow"
        def swT = topologyHelper.findSwitchTripletWithAlternativeFirstPortPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def allPotentialPaths = swT.pathsEp1 + swT.pathsEp2
        def oldPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def firstIslPorts = oldPaths.subFlowPaths*.forward*.first().outputPort as Set

        when: "Bring all ports down on the shared switch that are involved in the current and alternative paths"
        List<PathNode> broughtDownPorts = []
        allPotentialPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }

        then: "The HA-flow goes to 'Down' status"
        wait(rerouteDelay + WAIT_OFFSET) {
            haFlowHelper.assertHaFlowAndSubFlowStatuses(haFlow.haFlowId, FlowState.DOWN)
            // TODO check failed reroute in history https://github.com/telstra/open-kilda/issues/5169
        }

        when: "Bring all ports up on the shared switch that are involved in the alternative paths"
        broughtDownPorts.findAll {
            !firstIslPorts.contains(it.portNo)
        }.each {
            antiflap.portUp(it.switchId, it.portNo)
        }
        def broughtDownPortsUp = true

        then: "The HA-flow goes to 'Up' state and the HA-flow was rerouted"
        def newPaths = null
        wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            haFlowHelper.assertHaFlowAndSubFlowStatuses(haFlow.haFlowId, FlowState.UP)
            newPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
            newPaths != oldPaths
        }

        and: "HA-flow passes validation"
        northboundV2.validateHaFlow(haFlow.haFlowId).asExpected

        and: "All involved switches pass switch validation"
        def allInvolvedSwitchIds = haFlowHelper.getInvolvedSwitches(oldPaths) + haFlowHelper.getInvolvedSwitches(newPaths)
        withPool {
            allInvolvedSwitchIds.eachParallel { SwitchId switchId ->
                northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup: "Bring port involved in the original path up and delete the HA-flow"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        !broughtDownPortsUp && broughtDownPorts.each { antiflap.portUp(it.switchId, it.portNo) }
        oldPaths && broughtDownPortsUp && firstIslPorts.each { antiflap.portUp(haFlow.sharedEndpoint.switchId, it) }
        wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
    }

    @Tidy
    @Tags(SMOKE)
    def "HA-flow goes to 'Down' status when ISl of the HA-flow fails and there is no alt path to reroute"() {
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        given: "An HA-flow without alternative paths"
        def swT = topologyHelper.findSwitchTripletWithDifferentEndpoints()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))
        def oldPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def currentPathNodes = oldPaths.subFlowPaths*.forward*.first()
                .collect {new PathNode(it.switchId, it.outputPort, 0) } as Set
        def currentIslsToFail = oldPaths.subFlowPaths*.forward
                .collect { pathHelper.getInvolvedIsls(PathHelper.convert(it)).first() }.unique()
        def allPotentialPaths = swT.pathsEp1 + swT.pathsEp2

        and: "All ISL ports on the shared switch that are involved in the current HA-flow paths are down"
        def alternativePaths = allPotentialPaths.unique { it.first() }
                .findAll { !currentPathNodes.contains(it) }

        withPool {
            alternativePaths*.first().each {
                antiflap.portDown(it.switchId, it.portNo)
            }
        }
        def alternativeIsls = alternativePaths.collect { pathHelper.getInvolvedIsls(it).first() }
        waitForIslsFail(alternativeIsls)

        when: "Bring port down of ISL which is involved in the current HA-flow paths"
        withPool {
            currentPathNodes.each {
                antiflap.portDown(it.switchId, it.portNo)
            }
        }
        waitForIslsFail(currentIslsToFail)

        then: "The HA-flow goes to 'Down' status"
        wait(rerouteDelay + WAIT_OFFSET) {
            haFlowHelper.assertHaFlowAndSubFlowStatuses(haFlow.haFlowId, FlowState.DOWN)
            // TODO check failed reroute in history https://github.com/telstra/open-kilda/issues/5169
        }

        and: "All involved switches pass switch validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(oldPaths).eachParallel { SwitchId switchId ->
                northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup: "Bring port involved in the original path up and delete the HA-flow"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        alternativePaths && alternativePaths*.first().each {antiflap.portUp(it.switchId, it.portNo) }
        currentPathNodes && currentPathNodes.each {antiflap.portUp(it.switchId, it.portNo) }
        wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
    }

    private boolean waitForIslsFail(List<Isl> islsToFail) {
        wait(WAIT_OFFSET) {
            withPool {
                islsToFail.each {
                    assert northbound.getLink(it).state == FAILED
                }
            }
        }
    }
}
