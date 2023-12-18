package org.openkilda.functionaltests.spec.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.Message
import org.openkilda.messaging.command.CommandData
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.model.SwitchPropertiesDto.RttState
import org.openkilda.model.SwitchFeature
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Unroll

class DefaultRulesSpec extends HealthCheckSpecification {

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    @Unroll("Default rules are installed on #sw.hwSwString")
    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Default rules are installed on switches"() {
        expect: "Default rules are installed on the switch"
        def cookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == sw.defaultCookies.sort()

        where:
        sw << getTopology().getActiveSwitches().unique { sw -> sw.description }
    }

    @Tags([SMOKE])
    def "Default rules are installed when a new switch is connected"() {
        given: "A switch with no rules installed and not connected to the controller"
        def sw = topology.activeSwitches.first()
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.isEmpty() }
        def blockData = switchHelper.knockoutSwitch(sw, RW)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(sw, blockData)
        def switchIsActivated = true

        then: "Default rules are installed on the switch"
        wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
        }
        def testIsCompleted = true

        cleanup:
        blockData && !switchIsActivated && switchHelper.reviveSwitch(sw, blockData)
        if (!testIsCompleted) {
            northbound.synchronizeSwitch(sw.dpId, true)
            wait(RULES_INSTALLATION_TIME) {
                assertThat(northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.toArray()).as(sw.dpId.toString())
                        .containsExactlyInAnyOrder(*sw.defaultCookies)
            }
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to install default rule on #sw.hwSwString [install-action=#data.installRulesAction]"(
            Map data, Switch sw) {
        given: "A switch without any rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assertThat(defaultRules*.cookie.sort()).containsExactlyInAnyOrder(*sw.defaultCookies.sort())

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, data.installRulesAction)

        then: "The corresponding rules are really installed"
        //https://github.com/telstra/open-kilda/issues/3625
//        installedRules.size() == 1

        def expectedRules = defaultRules.findAll { it.cookie == data.cookie }
        wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries
                    .findAll { new Cookie(it.cookie).getType() != CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES },
                    expectedRules)
        }

        cleanup: "Install missing default rules"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        wait(RULES_INSTALLATION_TIME + discoveryInterval) {
            assertThat(northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.toArray())
                    .containsExactlyInAnyOrder(*defaultRules*.cookie.sort())
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
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
                getTopology().getActiveSwitches().unique { activeSw -> activeSw.description }
        ].combinations()
        .findAll { dataPiece, theSw ->
            //OF_12 has only broadcast rule, so filter out all other combinations for OF_12
            !(theSw.ofVersion == "OF_12" && dataPiece.installRulesAction != InstallRulesAction.INSTALL_BROADCAST) &&
                    //BFD, Round Trip and VXlan are available only on Noviflow
                    !(!theSw.noviflow && dataPiece.installRulesAction in [InstallRulesAction.INSTALL_BFD_CATCH,
                                                                  InstallRulesAction.INSTALL_ROUND_TRIP_LATENCY,
                                                                  InstallRulesAction.INSTALL_UNICAST_VXLAN]) &&
                    //having broadcast rule with 'drop loop' rule on WB5164 will lead to packet storm. See #2595
                    !(theSw.wb5164 && dataPiece.installRulesAction == InstallRulesAction.INSTALL_BROADCAST)
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to install default rule on #sw.hwSwString [install-action=#data.installRulesAction]"(
            Map data, Switch sw) {
        given: "A switch without rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, data.installRulesAction)

        then: "The corresponding rules are really installed"
        installedRules.size() == 1

        def expectedRules = defaultRules.findAll { it.cookie == data.cookie }
        wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries, expectedRules)
        }

        cleanup: "Install missing default rules and restore switch properties"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        wait(RULES_INSTALLATION_TIME + discoveryInterval) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.size() == defaultRules.size()
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
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
                getTopology().getActiveSwitches().findAll {
                    it.noviflow || it.virtual
                }.unique { activeSw -> activeSw.description }
        ].combinations()
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Able to install default rules on #sw.hwSwString [install-action=INSTALL_DEFAULTS]"() {
        given: "A switch without any rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)

        then: "The corresponding rules are really installed"
        installedRules.size() == defaultRules.size()
        wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries, defaultRules)
        }
        def testIsCompleted = true

        cleanup:
        if (!testIsCompleted) {
            northbound.synchronizeSwitch(sw.dpId, true)
            wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }


        where:
        sw << getTopology().getActiveSwitches().unique { sw -> sw.description }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Able to delete default rule from #sw.hwSwString[delete-action=#data.deleteRulesAction]"(
            Map data, Switch sw) {
        when: "Delete rules from the switch"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()
        def deletedRules = northbound.deleteSwitchRules(sw.dpId, data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == 1
        wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert actualRules.findAll { it.cookie in deletedRules }.empty
            compareRules(actualRules, defaultRules.findAll { it.cookie != data.cookie })
        }

        and: "Switch and rules validation shows that corresponding default rule is missing"
        verifyAll(northbound.validateSwitchRules(sw.dpId)) {
            missingRules == deletedRules
            excessRules.empty
            properRules.sort() == sw.defaultCookies.findAll { it != data.cookie }.sort()
        }
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            rules.missing == deletedRules
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper.sort() == sw.defaultCookies.findAll { it != data.cookie }.sort()
        }

        cleanup: "Install default rules back"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.size() == defaultRules.size()
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
                getTopology().getActiveSwitches().unique { activeSw -> activeSw.description }
        ].combinations().findAll { dataPiece, theSw ->
            //OF_12 switches has only one broadcast rule, so not all iterations will be applicable
            !(theSw.ofVersion == "OF_12" && dataPiece.cookie != Cookie.VERIFICATION_BROADCAST_RULE_COOKIE) &&
                    //dropping this rule on WB5164 will lead to disco-packet storm. Reason: #2595
            !(theSw.wb5164 && dataPiece.cookie == Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE)
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to delete default rule from #sw.hwSwString [delete-action=#data.deleteRulesAction]"(Map data, Switch sw) {
        when: "Delete rule from the switch"
        def defaultRules
        wait(RULES_INSTALLATION_TIME) {
            defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()
        }
        def deletedRules = northbound.deleteSwitchRules(sw.dpId, data.deleteRulesAction)

        then: "The corresponding rule is really deleted"
        deletedRules.size() == 1
        wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert actualRules.findAll { it.cookie in deletedRules }.empty
            compareRules(actualRules, defaultRules.findAll { it.cookie != data.cookie })
        }

        and: "Switch and rules validation shows that corresponding default rule is missing"
        verifyAll(northbound.validateSwitchRules(sw.dpId)) {
            missingRules == deletedRules
            excessRules.empty
            properRules.sort() == sw.defaultCookies.findAll { it != data.cookie }.sort()
        }
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            rules.missing == deletedRules
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper.sort() == sw.defaultCookies.findAll { it != data.cookie }.sort()
        }

        cleanup: "Install default rules back"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.size() == defaultRules.size()
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
                getTopology().getActiveSwitches().findAll {
                    it.noviflow || it.virtual
                }.unique { activeSw -> activeSw.description }
        ].combinations()
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to delete/install the server42 Flow RTT turning rule on a switch"() {
        setup: "Select a switch which support server42 turning rule"
        def sw = topology.activeSwitches.find { it.features.contains(SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST) } ?:
                assumeTrue(false, "No suiting switch found")

        and: "Server42 is enabled in feature toggle"
        assumeTrue(northbound.getFeatureToggles().server42FlowRtt)

        when: "Delete the server42 turning rule from the switch"
        def deleteResponse = northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.REMOVE_SERVER_42_TURNING)

        then: "The delete rule response contains the server42 turning cookie only"
        deleteResponse.size() == 1
        deleteResponse[0] == SERVER_42_FLOW_RTT_TURNING_COOKIE

        and: "The corresponding rule is really deleted"
        wait(RULES_DELETION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll { it.cookie == SERVER_42_FLOW_RTT_TURNING_COOKIE }.empty
        }

        and: "Switch and rules validation shows that corresponding rule is missing"
        verifyAll(northbound.validateSwitchRules(sw.dpId)) {
            missingRules == [SERVER_42_FLOW_RTT_TURNING_COOKIE]
            excessRules.empty
            properRules.sort() == sw.defaultCookies.findAll { it != SERVER_42_FLOW_RTT_TURNING_COOKIE }.sort()
        }
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            rules.missing == [SERVER_42_FLOW_RTT_TURNING_COOKIE]
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper.sort() == sw.defaultCookies.findAll { it != SERVER_42_FLOW_RTT_TURNING_COOKIE }.sort()
        }

        when: "Install the server42 turning rule"
        def installResponse = northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_SERVER_42_TURNING)

        then: "The install rule response contains the server42 turning cookie only"
        installResponse.size() == 1
        installResponse[0] == SERVER_42_FLOW_RTT_TURNING_COOKIE

        and: "The corresponding rule is really installed"
        wait(RULES_INSTALLATION_TIME) {
            assert !northbound.getSwitchRules(sw.dpId).flowEntries.findAll { it.cookie == SERVER_42_FLOW_RTT_TURNING_COOKIE }.empty
        }
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to delete/install the server42 ISL RTT turning rule on a switch"() {
        setup: "Select a switch which support server42 turning rule"
        def sw = topology.getActiveServer42Switches().find(s -> switchHelper.getCachedSwProps(s.dpId).server42IslRtt != "DISABLED");
        assumeTrue(sw != null, "No suiting switch found")

        and: "Server42 is enabled in feature toggle"
        assumeTrue(northbound.getFeatureToggles().server42IslRtt)

        and: "server42IslRtt is enabled on the switch"
        def originSwProps = switchHelper.getCachedSwProps(sw.dpId)
        northbound.updateSwitchProperties(sw.dpId, originSwProps.jacksonCopy().tap({
            it.server42IslRtt = RttState.ENABLED.toString()
        }))
        wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                (it.cookie in [SERVER_42_ISL_RTT_TURNING_COOKIE, SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                        (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
            }.size() == northbound.getLinks(sw.dpId, null, null, null).size() + 2
        }

        when: "Delete the server42 ISL RTT turning rule from the switch"
        def deleteResponse = northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.REMOVE_SERVER_42_ISL_RTT_TURNING)

        then: "The delete rule response contains the server42 ISL RTT turning cookie only"
        deleteResponse.size() == 1
        deleteResponse[0] == SERVER_42_ISL_RTT_TURNING_COOKIE

        and: "The corresponding rule is really deleted"
        wait(RULES_DELETION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll { it.cookie == SERVER_42_ISL_RTT_TURNING_COOKIE }.empty
        }

        and: "Switch and rules validation shows that corresponding rule is missing"
        verifyAll(northbound.validateSwitchRules(sw.dpId)) {
            missingRules == [SERVER_42_ISL_RTT_TURNING_COOKIE]
            excessRules.empty
            properRules.sort() == sw.defaultCookies.findAll { it != SERVER_42_ISL_RTT_TURNING_COOKIE }.sort()
        }
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            rules.missing == [SERVER_42_ISL_RTT_TURNING_COOKIE]
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper.sort() == sw.defaultCookies.findAll { it != SERVER_42_ISL_RTT_TURNING_COOKIE }.sort()
        }

        when: "Install the server42 ISL RTT turning rule"
        def installResponse = northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_SERVER_42_ISL_RTT_TURNING)

        then: "The install rule response contains the server42 ISL RTT turning cookie only"
        installResponse.size() == 1
        installResponse[0] == SERVER_42_ISL_RTT_TURNING_COOKIE

        and: "The corresponding rule is really installed"
        wait(RULES_INSTALLATION_TIME) {
            assert !northbound.getSwitchRules(sw.dpId).flowEntries.findAll { it.cookie == SERVER_42_ISL_RTT_TURNING_COOKIE }.empty
        }

        cleanup: "Revert the feature toggle to init state"
        originSwProps && northbound.updateSwitchProperties(sw.dpId, originSwProps)
    }

    void compareRules(actualRules, expectedRules) {
        assert expect(actualRules.sort { it.cookie }, sameBeanAs(expectedRules.sort { it.cookie })
                .ignoring("byteCount")
                .ignoring("packetCount")
                .ignoring("durationNanoSeconds")
                .ignoring("durationSeconds"))
    }

    private static Message buildMessage(final CommandData data) {
        return new CommandMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null);
    }
}
