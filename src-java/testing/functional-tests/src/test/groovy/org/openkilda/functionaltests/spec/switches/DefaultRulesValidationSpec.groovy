package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import spock.lang.Narrative

import static org.hamcrest.Matchers.containsInAnyOrder
import static org.junit.Assert.assertThat
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE

@Narrative("""This test suite checks the switch validate and rule validate features regarding default rules.
System should be able to detect missing, misconfigured, proper and excess default rules.
The real-life usecase is that we should properly detect and distinguish 'duplicate' default rules with the same cookie
but different match/priority/etc.
""")

class DefaultRulesValidationSpec extends HealthCheckSpecification {
    //Tests for 'missing' default rules are in `DefaultRulesSpec`

    /* For now it is pretty difficult to test misconfigured and excess default rules from functional test level, thus
    test coverage is only provided on unit-test level (ValidationServiceImplTest)
     */

    @Tags(SMOKE)
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /single-table/)
    def "Switch and rule validation can properly detect default rules to 'proper' section (#sw.hwSwString #propsDescr)"(
            Map swProps, Switch sw, String propsDescr) {
        given: "Clean switch without customer flows and with the given switchProps"
        def originalProps = switchHelper.getCachedSwProps(sw.dpId)
        switchHelper.updateSwitchProperties(sw, originalProps.jacksonCopy().tap({
            it.switchLldp = swProps.switchLldp
            it.switchArp = swProps.switchArp
        }))

        expect: "Switch validation shows all expected default rules in 'proper' section"
        Wrappers.wait(Constants.RULES_INSTALLATION_TIME) {
            verifyAll(northbound.validateSwitchRules(sw.dpId)) {
                missingRules.empty
                excessRules.empty
                properRules.sort() == sw.defaultCookies.sort()
            }
        }

        and: "Rule validation shows all expected default rules in 'proper' section"
        verifyAll(switchHelper.validate(sw.dpId)) {
            rules.missing.empty
            rules.misconfigured.empty
            rules.excess.empty
            assertThat sw.toString(), rules.proper*.cookie, containsInAnyOrder(sw.defaultCookies.toArray())
        }

        where: "Run for all combinations of unique switches and switch modes"
        [swProps, sw] <<
                [[
                    [
                        switchLldp: false,
                        switchArp: false
                    ],
                    [
                        switchLldp: true,
                        switchArp: true
                    ]
                ], getTopology().getActiveSwitches().unique { activeSw -> activeSw.hwSwString }
                ].combinations()
        propsDescr = getDescr(swProps.switchLldp, swProps.switchArp)
    }

    String getDescr(boolean lldp, boolean arp) {
        String r = ""
        if (lldp && arp) {
            r += " with lldp and arp"
        } else if (lldp) {
            r += " with lldp"
        } else if (arp) {
            r += " with arp"
        }
        return r
    }
}
