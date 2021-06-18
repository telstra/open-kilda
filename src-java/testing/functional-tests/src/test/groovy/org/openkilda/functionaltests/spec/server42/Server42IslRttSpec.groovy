package org.openkilda.functionaltests.spec.server42

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

class Server42IslRttSpec extends HealthCheckSpecification {
    @Tidy
    def "Server42 ISL RTT rules are installed"() {
        given: "An active ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue("Was not able to find an ISL with a server42 connected", isl != null)

        when: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)
        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        then: "Server42 ISL RTT rules are installed"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            [isl.srcSwitch, isl.dstSwitch].each {
                assert northbound.getSwitchRules(it.dpId).flowEntries.findAll {
                    (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                            (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
                }.size() == (northbound.getLinks(it.dpId, null, null, null).size() + 2)
            }
        }

        and: "Involved switches pass switch validation"
        [isl.srcSwitch, isl.dstSwitch].each { sw ->
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                rules.missing.empty
                rules.excess.empty
                rules.misconfigured.empty
                meters.missing.empty
                meters.excess.empty
                meters.misconfigured.empty
            }
        }
        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    def changeIslRttSwitch(Switch sw, boolean islRttEnabled) {
        return changeIslRttSwitch(sw, islRttEnabled ? "ENABLED" : "DISABLED")
    }

    def changeIslRttSwitch(Switch sw, String requiredState) {
        def originalProps = northbound.getSwitchProperties(sw.dpId)
        if (originalProps.server42IslRtt != requiredState) {
            northbound.updateSwitchProperties(sw.dpId, originalProps.jacksonCopy().tap {
                server42IslRtt = requiredState
                def props = sw.prop ?: SwitchHelper.dummyServer42Props
                server42MacAddress = props.server42MacAddress
                server42Port = props.server42Port
                server42Vlan = props.server42Vlan
            })
        }
        return originalProps.server42IslRtt
    }

    def changeIslRttToggle(boolean requiredState) {
        def originalState = northbound.featureToggles.server42IslRtt
        if (originalState != requiredState) {
            northbound.toggleFeature(FeatureTogglesDto.builder().server42IslRtt(requiredState).build())
        }
        //not going to check rules on every switch in the system. sleep does the trick fine
        sleep(3000)
        return originalState
    }

    def revertToOrigin(islRttFeatureStartState, initialSwitchRtt) {
        islRttFeatureStartState != null && changeIslRttToggle(islRttFeatureStartState)
        initialSwitchRtt.each { sw, state -> changeIslRttSwitch(sw, state) }
        initialSwitchRtt.keySet().each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }
    }
}
