package org.openkilda.functionaltests.spec.multitable


import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.switchproperties.SwitchPropertiesNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.model.SwitchFeature

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/multi-table-pipelines")
class MultitableFlowsSpec extends HealthCheckSpecification {

    @Tidy
    @Tags(TOPOLOGY_DEPENDENT)
    def "System does not allow to enable the multiTable mode on an unsupported switch"() {
        given: "Unsupported switch"
        def sw = topology.activeSwitches.find { !it.features.contains(SwitchFeature.MULTI_TABLE) }
        assumeTrue(sw as boolean, "Unable to find required switch")

        when: "Try to enable the multiTable mode on the switch"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = true
        })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new SwitchPropertiesNotUpdatedExpectedError(
                "Failed to update switch properties.",
                ~/Switch ${sw.dpId} doesn\'t support requested feature MULTI_TABLE/).matches(exc)    }

    @Tags([LOW_PRIORITY, TOPOLOGY_DEPENDENT])
    @Ignore("wait until knockout switch is fixed for staging")
    def "System connects a new switch with disabled multiTable mode when the switch does not support that mode"() {
        given: "Unsupported switch"
        def sw = topology.activeSwitches.find { !it.features.contains(SwitchFeature.MULTI_TABLE) }
        assumeTrue(sw as boolean, "Unable to find required switch")

        and: "Multi table is enabled in the kilda configuration"
        def initConf = northbound.getKildaConfiguration()
        !initConf.useMultiTable && northbound.updateKildaConfiguration(northbound.getKildaConfiguration().tap {
            it.useMultiTable = true
        })
        def isls = topology.getRelatedIsls(sw)
        assert !northbound.getSwitchProperties(sw.dpId).multiTable

        when: "Disconnect the switch and remove it from DB. Pretend this switch never existed"
        def blockData = switchHelper.knockoutSwitch(sw, RW, true)
        isls.each { northbound.deleteLink(islUtils.toLinkParameters(it)) }
        northbound.deleteSwitch(sw.dpId, false)

        and: "New switch connects"
        switchHelper.reviveSwitch(sw, blockData, true)

        then: "Switch is added with disabled multiTable mode"
        !northbound.getSwitchProperties(sw.dpId).multiTable
        wait(RULES_INSTALLATION_TIME) {
            with(northbound.getSwitchRules(sw.dpId).flowEntries) { rules ->
                northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
                rules.findAll { it.instructions.goToTable }.empty
                rules.findAll { it.tableId }.empty
            }
        }

        and: "Cleanup: Revert system to origin state"
        !initConf.useMultiTable && northbound.updateKildaConfiguration(initConf)
    }
}
