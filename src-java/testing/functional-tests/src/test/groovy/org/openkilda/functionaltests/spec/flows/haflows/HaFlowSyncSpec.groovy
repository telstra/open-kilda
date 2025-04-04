package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowRuleEntity
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.messaging.payload.flow.FlowState

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

@Tags([HA_FLOW])
class HaFlowSyncSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Tags([SMOKE_SWITCHES, SMOKE])
    def "Able to synchronize an HA-Flow (install missing rules, reinstall existing). protectedPath=#data.protectedPath"() {
        given: "An HA-Flow with deleted rules on shared switch"
        def swT = data.protectedPath
                ? switchTriplets.all().findSwitchTripletForHaFlowWithProtectedPaths()
                : switchTriplets.all().withAllDifferentEndpoints().random()
        assumeTrue(swT != null, "Can't find required switch triplet")

        def haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(data.protectedPath)
                .build().create()

        def initialHaFlowPaths = haFlow.retrievedAllEntityPaths()
        def haFlowCookiesFromDB = haFlow.retrieveCookiesFromDb()
        def haFlowRulesToDelete = swT.shared.rulesManager.getRules().findAll { swRule ->
            haFlowCookiesFromDB.contains(swRule.getCookie())
        }
        assert !haFlowRulesToDelete.isEmpty()
        withPool {
            haFlowRulesToDelete.eachParallel { FlowRuleEntity rule -> swT.shared.rulesManager.delete(rule) }
        }

        Wrappers.wait(RULES_DELETION_TIME) {
            assert swT.shared.rulesManager.getRules().findAll { swRule ->
                haFlowCookiesFromDB.contains(swRule.getCookie())
            }.isEmpty()
        }

        when: "Synchronize the HA-Flow"
        def syncTime = new Date()
        def syncResponse = haFlow.sync()

        then: "The Ha-flow is synced"
        syncResponse.synced
        syncResponse.error == null
        syncResponse.unsyncedSwitches == null
        haFlow.waitForBeingInState(FlowState.UP)

        and: "The HA-Flow is not rerouted"
        haFlow.retrievedAllEntityPaths() == initialHaFlowPaths

        and: "The HA-Flow is valid"
        haFlow.validate().asExpected

        and: "Missing HA-Flow rules are installed (existing ones are reinstalled) on all switches"
        withPool {
            switches.all().findSwitchesInPath(initialHaFlowPaths).eachParallel { SwitchExtended sw ->
                Wrappers.wait(RULES_INSTALLATION_TIME) {
                    haRulesAreSynced(sw, haFlowCookiesFromDB, syncTime)
                }
            }
        }

        where: data << [
                [protectedPath: false],
                [protectedPath: true]
        ]
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Able to synchronize an HA-Flow if HA-Flow switch is inactive protectedPath=#data.protectedPath"() {
        given: "An HA-Flow with down shared endpoint"
        def swT = data.protectedPath
                ? switchTriplets.all().findSwitchTripletForHaFlowWithProtectedPaths()
                : switchTriplets.all().withAllDifferentEndpoints().random()
        assumeTrue(swT != null, "Can't find required switch triplet")

        def haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(data.protectedPath)
                .build().create()
        def initialHaFlowPaths = haFlow.retrievedAllEntityPaths()

        def downSwitch = swT.shared
        downSwitch.knockout(RW)

        haFlow.waitForBeingInState(FlowState.DOWN, rerouteDelay + FLOW_CRUD_TIMEOUT + WAIT_OFFSET)

        when: "Synchronize the HA-Flow"
        def syncTime = new Date()
        def syncResponse = haFlow.sync()

        then: "The HA-Flow is not synced"
        !syncResponse.synced
        !syncResponse.error.isEmpty()
        haFlow.waitForBeingInState(FlowState.DOWN)

        and: "Rules on down switch are not synced"
        syncResponse.unsyncedSwitches == [downSwitch.switchId] as Set

        and: "The HA-Flow is not rerouted"
        haFlow.retrievedAllEntityPaths() == initialHaFlowPaths

        and: "Missing HA-Flow rules are installed (existing ones are reinstalled) on UP involved switches"
        def upInvolvedSwitches = switches.all().findSpecific(initialHaFlowPaths.getInvolvedSwitches() - [downSwitch.switchId])
        def haFlowCookiesFromDB = haFlow.retrieveCookiesFromDb()
        withPool {
            upInvolvedSwitches.eachParallel { SwitchExtended sw ->
                Wrappers.wait(RULES_INSTALLATION_TIME) {
                    haRulesAreSynced(sw, haFlowCookiesFromDB, syncTime)
                }
            }
        }

        where: data << [
                [protectedPath: false],
                [protectedPath: true]
        ]
    }

    private void haRulesAreSynced(SwitchExtended sw, List<Long> haFlowCookiesFromDB, Date syncTime) {
        assert sw.validate().asExpected
        def haRules = sw.rulesManager.getRules().findAll { swRule ->
            haFlowCookiesFromDB.contains(swRule.getCookie())
        }
        assert !haRules.isEmpty()
        haRules.each {
            assert it.durationSeconds < TimeCategory.minus(new Date(), syncTime).toMilliseconds() / 1000.0
        }
    }
}
