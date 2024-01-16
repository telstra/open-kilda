package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class HaFlowSyncSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Autowired
    @Shared
    HaFlowHelper haPathHelper

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Tags([SMOKE_SWITCHES, SMOKE])
    def "Able to synchronize an HA-flow (install missing rules, reinstall existing). protectedPath=#data.protectedPath"() {
        given: "An HA-flow with deleted rules on shared switch"
        def swT = data.protectedPath
                ? topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
                : topologyHelper.findSwitchTripletWithDifferentEndpoints()
        assumeTrue(swT != null, "Can't find required switch triplet")

        def createRequest = haFlowHelper.randomHaFlow(swT)
        createRequest.allocateProtectedPath = data.protectedPath
        def haFlow = haFlowHelper.addHaFlow(createRequest)
        def initialHaFlowPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def switchToManipulate = swT.shared
        def switchRules = switchRulesFactory.get(switchToManipulate.dpId)
        def haFlowRulesToDelete = switchRules.forHaFlow(haFlow)
        assert !haFlowRulesToDelete.isEmpty()

        withPool {
            haFlowRulesToDelete.eachParallel { FlowEntry rule ->
                switchRules.delete(rule)
            }
        }

        Wrappers.wait(RULES_DELETION_TIME) {
            assert switchRulesFactory.get(switchToManipulate.dpId).forHaFlow(haFlow).isEmpty()
        }

        when: "Synchronize the HA-flow"
        def syncTime = new Date()
        def syncResponse = northboundV2.syncHaFlow(haFlow.haFlowId)
        Wrappers.wait(WAIT_OFFSET) {
            haFlowHelper.assertHaFlowAndSubFlowStatuses(haFlow.haFlowId, FlowState.UP)
        }

        then: "The HA-flow is synced"
        syncResponse.synced
        syncResponse.error == null
        syncResponse.unsyncedSwitches == null

        and: "The HA-flow is not rerouted"
        northboundV2.getHaFlowPaths(haFlow.haFlowId) == initialHaFlowPaths

        and: "The HA-flow is valid"
        northboundV2.validateHaFlow(haFlow.haFlowId).asExpected

        and: "Missing HA-flow rules are installed (existing ones are reinstalled) on all switches"
        withPool {
            haPathHelper.getInvolvedSwitches(initialHaFlowPaths).eachParallel { SwitchId swId ->
                Wrappers.wait(RULES_INSTALLATION_TIME) {
                    haRulesAreSynced(swId, haFlow, syncTime)
                }
            }
        }

        cleanup: "Delete the HA-flow"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        switchToManipulate && switchToManipulate.synchronize()

        where: data << [
                [protectedPath: false],
                [protectedPath: true]
        ]
    }

    def "Able to synchronize an HA-flow if HA-flow switch is inactive protectedPath=#data.protectedPath"() {
        given: "An HA-flow with down shared endpoint"
        def swT = data.protectedPath
                ? topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
                : topologyHelper.findSwitchTripletWithDifferentEndpoints()
        assumeTrue(swT != null, "Can't find required switch triplet")
        def createRequest = haFlowHelper.randomHaFlow(swT)
        createRequest.allocateProtectedPath = data.protectedPath
        def haFlow = haFlowHelper.addHaFlow(createRequest)
        def initialHaFlowPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def downSwitch = swT.shared
        def blockData = switchHelper.knockoutSwitch(downSwitch, RW)
        Wrappers.wait(rerouteDelay + FLOW_CRUD_TIMEOUT + WAIT_OFFSET) {
            assert haFlowHelper.getHaFlowStatus(haFlow.haFlowId) == FlowState.DOWN.toString()
        }

        when: "Synchronize the HA-flow"
        def syncTime = new Date()
        def syncResponse = northboundV2.syncHaFlow(haFlow.haFlowId)
        Wrappers.wait(WAIT_OFFSET) {
            haFlowHelper.assertHaFlowAndSubFlowStatuses(haFlow.haFlowId, FlowState.DOWN)
        }

        then: "The HA-flow is not synced"
        !syncResponse.synced
        !syncResponse.error.isEmpty()

        and: "Rules on don switch are not synced"
        syncResponse.unsyncedSwitches == [downSwitch.dpId] as Set

        and: "The HA-flow is not rerouted"
        northboundV2.getHaFlowPaths(haFlow.haFlowId) == initialHaFlowPaths

        and: "Missing HA-flow rules are installed (existing ones are reinstalled) on UP involved switches"
        def upInvolvedSwitches = haPathHelper.getInvolvedSwitches(initialHaFlowPaths) - [downSwitch.dpId]
        withPool {
            upInvolvedSwitches.eachParallel { SwitchId swId ->
                Wrappers.wait(RULES_INSTALLATION_TIME) {
                    haRulesAreSynced(swId, haFlow, syncTime)
                }
            }
        }

        cleanup: "Delete the HA-flow"
        downSwitch && blockData && switchHelper.reviveSwitch(downSwitch, blockData, true)
        haFlow && Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert haFlowHelper.getHaFlowStatus(haFlow.haFlowId) != FlowState.IN_PROGRESS.toString()
        }
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where: data << [
                [protectedPath: false],
                [protectedPath: true]
        ]
    }

    private void haRulesAreSynced(SwitchId swId, HaFlow haFlow, Date syncTime) {
        assert northboundV2.validateSwitch(swId).asExpected
        def haRules = switchRulesFactory.get(swId).forHaFlow(haFlow)
        assert !haRules.isEmpty()
        haRules.each {
            assert it.durationSeconds < TimeCategory.minus(new Date(), syncTime).toMilliseconds() / 1000.0
        }
    }
}
