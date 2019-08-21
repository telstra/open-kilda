package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags

import spock.lang.Narrative
import spock.lang.Unroll

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

    @Unroll
    @Tags(SMOKE)
    def "Switch and rule validation can properly detect default rules to 'proper' section (#sw.name)"() {
        given: "Clean switch without customer flows"
        expect: "Switch validation shows all expected default rules in 'proper' section"
        verifyAll(northbound.validateSwitchRules(sw.dpId)) {
            missingRules.empty
            excessRules.empty
            properRules == sw.defaultCookies
        }

        and: "Rule validation shows all expected default rules in 'proper' section"
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            rules.missing.empty
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper == sw.defaultCookies
        }

        where: "Run for all unique switches"
        sw << getTopology().getActiveSwitches().unique { activeSw -> activeSw.description }
    }
}
