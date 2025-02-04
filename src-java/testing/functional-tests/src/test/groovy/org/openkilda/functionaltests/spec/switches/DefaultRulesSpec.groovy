package org.openkilda.functionaltests.spec.switches

import static org.assertj.core.api.Assertions.assertThat
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.switches.Manufacturer.NOVIFLOW
import static org.openkilda.functionaltests.model.switches.Manufacturer.OVS
import static org.openkilda.messaging.command.switches.DeleteRulesAction.DROP_ALL
import static org.openkilda.messaging.command.switches.DeleteRulesAction.REMOVE_SERVER_42_ISL_RTT_TURNING
import static org.openkilda.messaging.command.switches.DeleteRulesAction.REMOVE_SERVER_42_TURNING
import static org.openkilda.model.SwitchFeature.KILDA_OVS_SWAP_FIELD
import static org.openkilda.model.SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE
import static org.openkilda.model.cookie.CookieBase.CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.model.SwitchPropertiesDto.RttState
import org.openkilda.model.cookie.Cookie

class DefaultRulesSpec extends HealthCheckSpecification {

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Default rules are installed on switches #sw.hwSwString()"() {
        expect: "Default rules are installed on the switch"
        def cookies = sw.rulesManager.getRules().cookie
        cookies.sort() == sw.collectDefaultCookies().sort()

        where:
        sw << switches.all().unique()
    }

    @Tags([SMOKE, SWITCH_RECOVER_ON_FAIL])
    def "Default rules are installed when a new switch is connected"() {
        given: "A switch with no rules installed and not connected to the controller"
        def sw = switches.all().random()
        sw.rulesManager.delete(DROP_ALL)
        wait(RULES_DELETION_TIME) { assert sw.rulesManager.getRules().isEmpty() }
        def blockData = sw.knockout(RW)

        when: "Connect the switch to the controller"
        sw.revive(blockData)

        then: "Default rules are installed on the switch"
        wait(RULES_INSTALLATION_TIME) {
            assert sw.rulesManager.getRules().cookie.sort() == sw.collectDefaultCookies().sort()
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to install default rule on #sw.hwSwString() [install-action=#data.installRulesAction]"(
            Map data, SwitchExtended sw) {
        given: "A switch without any rules"
        def defaultRules = sw.rulesManager.getRules()
        assertThat(defaultRules*.cookie.sort()).containsExactlyInAnyOrder(*sw.collectDefaultCookies().sort())

        sw.rulesManager.delete(DROP_ALL)
        wait(RULES_DELETION_TIME) { assert sw.rulesManager.getRules().empty }

        when: "Install rules on the switch"
        def installedRules = sw.rulesManager.install(data.installRulesAction)

        then: "The corresponding rules are really installed"
        installedRules.size() == 1

        def expectedRules = defaultRules.findAll { it.cookie == data.cookie }
        wait(RULES_INSTALLATION_TIME) {
            def actualRules = sw.rulesManager.getRules()
                    .findAll { new Cookie(it.cookie).getType() != MULTI_TABLE_ISL_VLAN_EGRESS_RULES }
            assertThat(actualRules).containsExactlyInAnyOrder(*expectedRules)
        }

        where:
        [data, sw] << [
                [
                        [
                                installRulesAction: InstallRulesAction.INSTALL_DROP,
                                cookie            : Cookie.DROP_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_BROADCAST,
                                cookie            : Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_UNICAST,
                                cookie            : Cookie.VERIFICATION_UNICAST_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_BFD_CATCH,
                                cookie            : Cookie.CATCH_BFD_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_ROUND_TRIP_LATENCY,
                                cookie            : Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_UNICAST_VXLAN,
                                cookie            : Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
                        ]
                ],
                switches.all().unique()
        ].combinations()
                .findAll { dataPiece, SwitchExtended theSw ->
                    //OF_12 has only broadcast rule, so filter out all other combinations for OF_12
                    !(theSw.ofVersion == "OF_12" && dataPiece.installRulesAction != InstallRulesAction.INSTALL_BROADCAST) &&
                            //BFD, Round Trip and VXlan are available only on Noviflow
                            !(!theSw.isNoviflow() && dataPiece.installRulesAction in [InstallRulesAction.INSTALL_BFD_CATCH,
                                                                                  InstallRulesAction.INSTALL_ROUND_TRIP_LATENCY,
                                                                                  InstallRulesAction.INSTALL_UNICAST_VXLAN]) &&
                            //having broadcast rule with 'drop loop' rule on WB5164 will lead to packet storm. See #2595
                            !(theSw.isWb5164() && dataPiece.installRulesAction == InstallRulesAction.INSTALL_BROADCAST)
                }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to install default rule on switch: #sw.hwSwString() [install-action=#data.installRulesAction]"(
            Map data, SwitchExtended sw) {
        given: "A switch without rules"
        def defaultRules = sw.rulesManager.getRules()
        assert defaultRules*.cookie.sort() == sw.collectDefaultCookies().sort()

        sw.rulesManager.delete(DROP_ALL)
        wait(RULES_DELETION_TIME) { assert sw.rulesManager.getRules().empty }

        when: "Install rules on the switch"
        def installedRules = sw.rulesManager.install(data.installRulesAction)

        then: "The corresponding rules are really installed"
        installedRules.size() == 1

        def expectedRules = defaultRules.findAll { it.cookie == data.cookie }
        wait(RULES_INSTALLATION_TIME) {
            def actualRules = sw.rulesManager.getRules()
            assert actualRules.cookie == installedRules
            assertThat(actualRules).containsExactlyInAnyOrder(*expectedRules)
        }

        where:
        [data, sw] << [
                [
                        [
                                installRulesAction: InstallRulesAction.INSTALL_MULTITABLE_PRE_INGRESS_PASS_THROUGH,
                                cookie            : Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_MULTITABLE_INGRESS_DROP,
                                cookie            : Cookie.MULTITABLE_INGRESS_DROP_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_MULTITABLE_POST_INGRESS_DROP,
                                cookie            : Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_MULTITABLE_EGRESS_PASS_THROUGH,
                                cookie            : Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_MULTITABLE_TRANSIT_DROP,
                                cookie            : Cookie.MULTITABLE_TRANSIT_DROP_COOKIE
                        ]
                ],
                (profile == "virtual" ?
                        switches.all().withManufacturer(OVS).unique() :
                        switches.all().withManufacturer(NOVIFLOW).unique())
        ].combinations()
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Able to install default rules on #sw.hwSwString() [install-action=INSTALL_DEFAULTS]"() {
        given: "A switch without any rules"
        def defaultRules = sw.rulesManager.getRules()
        assert defaultRules*.cookie.sort() == sw.collectDefaultCookies().sort()

        sw.rulesManager.delete(DROP_ALL)
        wait(RULES_DELETION_TIME) { assert sw.rulesManager.getRules().empty }

        when: "Install rules on the switch"
        def installedRules = sw.rulesManager.install(InstallRulesAction.INSTALL_DEFAULTS)

        then: "The corresponding rules are really installed"
        installedRules.size() == defaultRules.size()
        wait(RULES_INSTALLATION_TIME) {
            def actualRules = sw.rulesManager.getRules()
            assertThat(actualRules).containsExactlyInAnyOrder(*defaultRules)
        }

        where:
        sw << switches.all().unique()
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Able to delete default rule from #sw.hwSwString()[delete-action=#data.deleteRulesAction]"(
            Map data, SwitchExtended sw) {
        when: "Delete rules from the switch"
        def defaultRules = sw.rulesManager.getRules()
        def expectedDefaultCookies = sw.collectDefaultCookies()
        assert defaultRules*.cookie.sort() == expectedDefaultCookies.sort()
        def deletedRules = sw.rulesManager.delete(data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == 1
        wait(RULES_DELETION_TIME) {
            def actualRules = sw.rulesManager.getRules()
            assertThat(actualRules).containsExactlyInAnyOrder(*defaultRules.findAll { it.cookie != data.cookie })
        }

        and: "Switch and rules validation shows that corresponding default rule is missing"
        verifyAll(sw.rulesManager.validate()) {
            missingRules == deletedRules
            excessRules.empty
            properRules.sort() == expectedDefaultCookies.findAll { it != data.cookie }.sort()
        }

        verifyAll(sw.validate()) {
            rules.missing*.getCookie() == deletedRules
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper*.getCookie().sort() == expectedDefaultCookies.findAll { it != data.cookie }.sort()
        }

        where:
        [data, sw] << [
                [
                        [// Drop just the default / base drop rule
                         deleteRulesAction: DeleteRulesAction.REMOVE_DROP,
                         cookie           : Cookie.DROP_RULE_COOKIE
                        ],
                        [// Drop just the verification (broadcast) rule only
                         deleteRulesAction: DeleteRulesAction.REMOVE_BROADCAST,
                         cookie           : Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
                        ],
                        [// Drop just the verification (unicast) rule only
                         deleteRulesAction: DeleteRulesAction.REMOVE_UNICAST,
                         cookie           : Cookie.VERIFICATION_UNICAST_RULE_COOKIE
                        ],
                        [// Remove the verification loop drop rule only
                         deleteRulesAction: DeleteRulesAction.REMOVE_VERIFICATION_LOOP,
                         cookie           : Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE
                        ]
                ],
                switches.all().unique()
        ].combinations().findAll { dataPiece, SwitchExtended theSw ->
            //OF_12 switches has only one broadcast rule, so not all iterations will be applicable
            !(theSw.ofVersion == "OF_12" && dataPiece.cookie != Cookie.VERIFICATION_BROADCAST_RULE_COOKIE) &&
                    //dropping this rule on WB5164 will lead to disco-packet storm. Reason: #2595
            !(theSw.isWb5164() && dataPiece.cookie == Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE)
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to delete default rule from #sw.hwSwString() [delete-action=#data.deleteRulesAction]"(Map data, SwitchExtended sw) {
        when: "Delete rule from the switch"
        def defaultRules
        def expectedDefaultCookies = sw.collectDefaultCookies()
        wait(RULES_INSTALLATION_TIME) {
            defaultRules = sw.rulesManager.getRules()
            assert defaultRules*.cookie.sort() == expectedDefaultCookies.sort()
        }
        def deletedRules = sw.rulesManager.delete(data.deleteRulesAction)

        then: "The corresponding rule is really deleted"
        deletedRules.size() == 1
        wait(RULES_DELETION_TIME) {
            def actualRules = sw.rulesManager.getRules()
            assertThat(actualRules).containsExactlyInAnyOrder(*defaultRules.findAll { it.cookie != data.cookie })
        }

        and: "Switch and rules validation shows that corresponding default rule is missing"
        verifyAll(sw.rulesManager.validate()) {
            missingRules == deletedRules
            excessRules.empty
            properRules.sort() == expectedDefaultCookies.findAll { it != data.cookie }.sort()
        }

        verifyAll(sw.validate()) {
            rules.missing*.getCookie() == deletedRules
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper*.getCookie().sort() == expectedDefaultCookies.findAll { it != data.cookie }.sort()
        }

        where:
        [data, sw] << [
                [
                        [
                                deleteRulesAction: DeleteRulesAction.REMOVE_MULTITABLE_PRE_INGRESS_PASS_THROUGH,
                                cookie           : Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE
                        ],
                        [
                                deleteRulesAction: DeleteRulesAction.REMOVE_MULTITABLE_INGRESS_DROP,
                                cookie           : Cookie.MULTITABLE_INGRESS_DROP_COOKIE
                        ],
                        [
                                deleteRulesAction: DeleteRulesAction.REMOVE_MULTITABLE_POST_INGRESS_DROP,
                                cookie           : Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE
                        ],
                        [
                                deleteRulesAction: DeleteRulesAction.REMOVE_MULTITABLE_EGRESS_PASS_THROUGH,
                                cookie           : Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE
                        ],
                        [
                                deleteRulesAction: DeleteRulesAction.REMOVE_MULTITABLE_TRANSIT_DROP,
                                cookie           : Cookie.MULTITABLE_TRANSIT_DROP_COOKIE
                        ]
                ],
                (profile == "virtual" ?
                        switches.all().withManufacturer(OVS).unique() :
                        switches.all().withManufacturer(NOVIFLOW).unique())
        ].combinations()
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to delete/install the server42 Flow RTT turning rule on a switch"() {
        setup: "Select a switch which support server42 turning rule"
        def sw = switches.all().withS42Support().first()
        def features = sw.getDbFeatures()
        assert features.contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST) || features.contains(KILDA_OVS_SWAP_FIELD)

        and: "Server42 is enabled in feature toggle"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        sw.waitForS42FlowRttRulesSetup()

        when: "Delete the server42 turning rule from the switch"
        def deleteResponse = sw.rulesManager.delete(REMOVE_SERVER_42_TURNING)

        then: "The delete rule response contains the server42 turning cookie only"
        deleteResponse.size() == 1
        deleteResponse[0] == SERVER_42_FLOW_RTT_TURNING_COOKIE

        and: "The corresponding rule is really deleted"
        wait(RULES_DELETION_TIME) {
            assert sw.rulesManager.getRules().findAll { it.cookie == SERVER_42_FLOW_RTT_TURNING_COOKIE }.empty
        }

        and: "Switch and rules validation shows that corresponding rule is missing"
        def defaultCookiesWithoutMissingS42Rule = sw.collectDefaultCookies().findAll { it != SERVER_42_FLOW_RTT_TURNING_COOKIE }.sort()
        verifyAll(sw.rulesManager.validate()) {
            missingRules == [SERVER_42_FLOW_RTT_TURNING_COOKIE]
            excessRules.empty
            properRules.sort() == defaultCookiesWithoutMissingS42Rule
        }

        verifyAll(sw.validate()) {
            rules.missing*.getCookie() == [SERVER_42_FLOW_RTT_TURNING_COOKIE]
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper*.getCookie().sort() == defaultCookiesWithoutMissingS42Rule
        }

        when: "Install the server42 turning rule"
        def installResponse = sw.rulesManager.install(InstallRulesAction.INSTALL_SERVER_42_TURNING)

        then: "The install rule response contains the server42 turning cookie only"
        installResponse.size() == 1
        installResponse[0] == SERVER_42_FLOW_RTT_TURNING_COOKIE

        and: "The corresponding rule is really installed"
        wait(RULES_INSTALLATION_TIME) {
            assert !sw.rulesManager.getRules().findAll { it.cookie == SERVER_42_FLOW_RTT_TURNING_COOKIE }.empty
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to delete/install the server42 ISL RTT turning rule on a switch"() {
        setup: "Select a switch which support server42 turning rule"
        def sw = switches.all().withS42Support().random()

        and: "Server42 is enabled in feature toggle"
        !featureToggles.getFeatureToggles().server42IslRtt && featureToggles.server42IslRtt(true)

        and: "server42IslRtt is enabled on the switch"
        def originSwProps = sw.getCashedProps()
        sw.updateProperties(originSwProps.jacksonCopy().tap({ it.server42IslRtt = RttState.ENABLED.toString() }))

        wait(RULES_INSTALLATION_TIME) {
            assert sw.rulesManager.getServer42ISLRelatedRules().size() == sw.getRelatedLinks().size() + 2
        }

        when: "Delete the server42 ISL RTT turning rule from the switch"
        def deleteResponse = sw.rulesManager.delete(REMOVE_SERVER_42_ISL_RTT_TURNING)

        then: "The delete rule response contains the server42 ISL RTT turning cookie only"
        deleteResponse.size() == 1
        deleteResponse[0] == SERVER_42_ISL_RTT_TURNING_COOKIE

        and: "The corresponding rule is really deleted"
        wait(RULES_DELETION_TIME) {
            assert sw.rulesManager.getRules().findAll { it.cookie == SERVER_42_ISL_RTT_TURNING_COOKIE }.empty
        }

        and: "Switch and rules validation shows that corresponding rule is missing"
        verifyAll(sw.rulesManager.validate()) {
            missingRules == [SERVER_42_ISL_RTT_TURNING_COOKIE]
            excessRules.empty
            properRules.sort() == sw.collectDefaultCookies().findAll { it != SERVER_42_ISL_RTT_TURNING_COOKIE }.sort()
        }

        verifyAll(sw.validate()) {
            rules.missing*.getCookie() == [SERVER_42_ISL_RTT_TURNING_COOKIE]
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper*.getCookie().sort() == sw.collectDefaultCookies().findAll { it != SERVER_42_ISL_RTT_TURNING_COOKIE }.sort()
        }

        when: "Install the server42 ISL RTT turning rule"
        def installResponse = sw.rulesManager.install(InstallRulesAction.INSTALL_SERVER_42_ISL_RTT_TURNING)

        then: "The install rule response contains the server42 ISL RTT turning cookie only"
        installResponse.size() == 1
        installResponse[0] == SERVER_42_ISL_RTT_TURNING_COOKIE

        and: "The corresponding rule is really installed"
        wait(RULES_INSTALLATION_TIME) {
            assert !sw.rulesManager.getRules().findAll { it.cookie == SERVER_42_ISL_RTT_TURNING_COOKIE }.empty
        }
    }
}
