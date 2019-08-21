package org.openkilda.functionaltests.spec.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Message
import org.openkilda.messaging.command.CommandData
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.model.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

class DefaultRulesSpec extends HealthCheckSpecification {
    @Unroll("Default rules are installed on an #sw.ofVersion switch(#sw.dpId)")
    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Default rules are installed on switches"() {
        expect: "Default rules are installed on the switch"
        def cookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == sw.defaultCookies.sort()

        where:
        sw << getTopology().getActiveSwitches().unique { sw -> sw.description }
    }

    @Tags([VIRTUAL, SMOKE])
    def "Default rules are installed when a new switch is connected"() {
        given: "A switch with no rules installed and not connected to the controller"
        def sw = topology.activeSwitches.first()
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.isEmpty() }

        lockKeeper.knockoutSwitch(sw)
        Wrappers.wait(WAIT_OFFSET) { assert !(sw.dpId in northbound.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeper.reviveSwitch(sw)
        Wrappers.wait(WAIT_OFFSET) { assert sw.dpId in northbound.getActiveSwitches()*.switchId }

        then: "Default rules are installed on the switch"
        def cookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == sw.defaultCookies.sort()
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to install default rule on an #sw.ofVersion switch(#sw.dpId, install-action=#data.installRulesAction)"(
            Map data, Switch sw) {
        given: "A switch without any rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, data.installRulesAction)

        then: "The corresponding rules are really installed"
        installedRules.size() == 1

        def expectedRules = defaultRules.findAll { it.cookie == data.cookie }
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries, expectedRules)
        }

        and: "Install missing default rules"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.size() == defaultRules.size()
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
                                                                  InstallRulesAction.INSTALL_UNICAST_VXLAN])
                    //having broadcast rule with 'drop loop' rule on WB5164 will lead to packet storm. See #2595
                    !(!theSw.wb5164 && dataPiece.installRulesAction == InstallRulesAction.INSTALL_BROADCAST)
        }
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Able to install default rules on an #sw.ofVersion switch(#sw.dpId, install-action=INSTALL_DEFAULTS)"() {
        given: "A switch without any rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)

        then: "The corresponding rules are really installed"
        // TODO(ylobankov): For now BFD catch rule is returned in the list of installed rules even though the switch
        // doesn't have BFD support. Uncomment the check when the issue is resolved.
        //installedRules.size() == defaultRules.size()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries, defaultRules)
        }

        where:
        sw << getTopology().getActiveSwitches().unique { sw -> sw.description }
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT, SMOKE, SMOKE_SWITCHES])
    def "Able to delete default rule from an #sw.ofVersion switch (#sw.dpId, delete-action=#data.deleteRulesAction)"(
            Map data, Switch sw) {
        when: "Delete rules from the switch"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        assert defaultRules*.cookie.sort() == sw.defaultCookies.sort()
        def deletedRules = northbound.deleteSwitchRules(sw.dpId, data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == 1
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert actualRules.findAll { it.cookie in deletedRules }.empty
            compareRules(actualRules, defaultRules.findAll { it.cookie != data.cookie })
        }

        and: "Switch and rules validation shows that corresponding default rule is missing"
        with(northbound.validateSwitchRules(sw.dpId)) {
            missingRules == deletedRules
            excessRules.empty
            properRules == sw.defaultCookies.findAll { it != data.cookie }
        }
        with(northbound.validateSwitch(sw.dpId)) {
            rules.missing == deletedRules
            rules.misconfigured.empty
            rules.excess.empty
            rules.proper == sw.defaultCookies.findAll { it != data.cookie }
        }

        and: "Install default rules back"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
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
