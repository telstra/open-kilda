package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.model.Cookie
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Provides helper operations for some Switch-related content.
 * This helper is also injected as a Groovy Extension Module, so methods can be called in two ways:
 * <pre>
 * {@code
 * Switch sw = topology.activeSwitches[0]
 * def descriptionJ = SwitchHelper.getDescription(sw) //Java way
 * def descriptionG = sw.description //Groovysh way
 *}
 * </pre>
 */
@Component
class SwitchHelper {
    static NorthboundService northbound

    @Autowired
    SwitchHelper(NorthboundService northbound) {
        this.northbound = northbound
    }

    @Memoized
    static String getDescription(Switch sw) {
        northbound.getSwitch(sw.dpId).description
    }

    static List<Long> getDefaultCookies(Switch sw) {
        if (sw.noviflow) {
            return [Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE,
                    Cookie.CATCH_BFD_RULE_COOKIE]
        } else if (sw.ofVersion == "OF_12") {
            return [Cookie.VERIFICATION_BROADCAST_RULE_COOKIE]
        } else {
            return [Cookie.DROP_RULE_COOKIE, Cookie.VERIFICATION_BROADCAST_RULE_COOKIE,
                    Cookie.VERIFICATION_UNICAST_RULE_COOKIE, Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE]
        }
    }

    static boolean isCentec(Switch sw) {
        sw.description.toLowerCase().contains("centec")
    }

    static boolean isNoviflow(Switch sw) {
        sw.description.toLowerCase().contains("noviflow")
    }

    static boolean isVirtual(Switch sw) {
        sw.description.toLowerCase().contains("nicira")
    }

    static void verifyRulesOnProtectedPathCmnNodes(List<SwitchId> nodeIds, SwitchId srcSwitchId, SwitchId dstSwitchId,
                                                   List<PathNodePayload> mainFlowPath, List<PathNodePayload> protectedFlowPath) {
        nodeIds.each { sw ->
            def rules = northbound.getSwitchRules(sw).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            if (sw == srcSwitchId || sw == dstSwitchId) {
                assert rules.size() == 3

                def mainNode = mainFlowPath.find { it.switchId == sw }
                assert filterRulesForProtectedPath(rules, mainNode, true, false).size() == 1
                assert filterRulesForProtectedPath(rules, mainNode, false, true).size() == 1
                def protectedNode = protectedFlowPath.find { it.switchId == sw }
                //protected path creates an egress rule only
                if (protectedNode.switchId == srcSwitchId) {
                    assert filterRulesForProtectedPath(rules, protectedNode, true, false).size() == 0
                    assert filterRulesForProtectedPath(rules, protectedNode, false, true).size() == 1
                } else {
                    assert filterRulesForProtectedPath(rules, protectedNode, true, false).size() == 1
                    assert filterRulesForProtectedPath(rules, protectedNode, false, true).size() == 0
                }
            } else {
                assert rules.size() == 4

                def mainNode = mainFlowPath.find { it.switchId == sw }
                assert filterRulesForProtectedPath(rules, mainNode, true, false).size() == 1
                assert filterRulesForProtectedPath(rules, mainNode, false, true).size() == 1

                def protectedNode = protectedFlowPath.find { it.switchId == sw }
                assert filterRulesForProtectedPath(rules, protectedNode, true, false).size() == 1
                assert filterRulesForProtectedPath(rules, protectedNode, false, true).size() == 1
            }
        }
    }

    static void verifyRulesOnProtectedPathUniqueNodes(List<PathNodePayload> nodes) {
        nodes.each { sw ->
            def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            assert rules.size() == 2

            assert filterRulesForProtectedPath(rules, sw, true, false).size() == 1
            assert filterRulesForProtectedPath(rules, sw, false, true).size() == 1
        }
    }

    static List<FlowEntry> filterRulesForProtectedPath(List<FlowEntry> rules, PathNodePayload node, Boolean ingress,
                                                       Boolean egress) {
        if (ingress) {
            rules = rules.findAll {
                it.match.inPort == node.inputPort.toString() &&
                        it.instructions.applyActions.flowOutput == node.outputPort.toString()
            }
        }
        if (egress) {
            rules = rules.findAll {
                it.instructions?.applyActions?.flowOutput == node.inputPort.toString() &&
                        it.match.inPort == node.outputPort.toString()
            }
        }

        return rules
    }
}
