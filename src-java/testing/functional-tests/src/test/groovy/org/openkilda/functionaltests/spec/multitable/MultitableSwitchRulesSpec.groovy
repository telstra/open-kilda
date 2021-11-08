package org.openkilda.functionaltests.spec.multitable

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.model.SwitchFeature
import org.openkilda.model.cookie.Cookie

import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/multi-table-pipelines")
class MultitableSwitchRulesSpec extends HealthCheckSpecification {
    @Tags([SMOKE_SWITCHES, LOW_PRIORITY])
    def "Switch migration to multi table mode and vice-versa leave no discrepancies in default rules"() {
        given: "An active switch with disabled multi-table mode"
        def sw = topology.activeSwitches.find { it.features.contains(SwitchFeature.MULTI_TABLE) }
        def initSwProps = northbound.getSwitchProperties(sw.dpId)
        initSwProps.multiTable && northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = false
        })
        def initSwitchRules
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            initSwitchRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert initSwitchRules*.cookie.sort() == sw.defaultCookies.sort()
            assert initSwitchRules*.instructions.findAll { it.goToTable }.empty
            assert initSwitchRules.findAll { it.tableId }.empty
        }

        when: "Update switch properties(multi_table: true)"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = true
        })
        def newSwitchRules
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            newSwitchRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert newSwitchRules*.cookie.sort() == sw.defaultCookies.sort()
        }

        then: "Default rules are recreated in multi table mode"
        with(newSwitchRules.findAll { new Cookie(it.cookie).serviceFlag }) { rules ->
            rules*.tableId.unique().sort() == [0, 1, 2, 3, 4, 5]
            rules*.instructions.findAll { it.goToTable }.goToTable.unique().sort() == [2, 4, 5]
        }

        and: "Switch pass switch validation"
        with(northbound.validateSwitch(sw.dpId)) { validationResponse ->
            validationResponse.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        when: "Update switch properties(multi_table: false)"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = false
        })
        def latestSwitchRules
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            latestSwitchRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert latestSwitchRules*.cookie.sort() == sw.defaultCookies.sort()
        }

        then: "Default rules are recreated in single table mode"
        with(latestSwitchRules.findAll { new Cookie(it.cookie).serviceFlag }) { rules ->
            rules.findAll { it.instructions.goToTable }.empty
            rules.findAll { it.tableId }.empty
        }

        and: "Switch pass switch validation"
        with(northbound.validateSwitch(sw.dpId)) { validationResponse ->
            validationResponse.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        cleanup: "Revert system to origin state"
        northbound.updateSwitchProperties(sw.dpId, initSwProps)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
        }
    }
}
