package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.model.Cookie
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

    static void verifyRulesOnProtectedFlow(String flowId) {
        def mainFlowPath = northbound.getFlowPath(flowId).forwardPath
        def srcMainNode = mainFlowPath[0]
        def dstMainNode = mainFlowPath[-1]
        def mainFlowTransitNodes = mainFlowPath[1..-2]
        def protectedFlowPath = northbound.getFlowPath(flowId).protectedPath.forwardPath
        def srcProtectedNode = protectedFlowPath[0]
        def dstProtectedNode = protectedFlowPath[-1]
        def protectedFlowTransitNodes = protectedFlowPath[1..-2]

        def commonNodes = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)
        def commonTransitSwitches = mainFlowTransitNodes*.switchId.intersect(protectedFlowTransitNodes*.switchId)

        def rulesOnSrcSwitch = northbound.getSwitchRules(srcMainNode.switchId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }
        assert rulesOnSrcSwitch.size() == 3
        //rules for main path
        assert findInputRules(rulesOnSrcSwitch, srcMainNode).size() == 1
        assert findOutputRules(rulesOnSrcSwitch, srcMainNode).size() == 1
        //rule for protected path
        assert findInputRules(rulesOnSrcSwitch, srcProtectedNode).size() == 0
        assert findOutputRules(rulesOnSrcSwitch, srcProtectedNode).size() == 1

        def rulesOnDstSwitch = northbound.getSwitchRules(dstMainNode.switchId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }
        assert rulesOnDstSwitch.size() == 3
        //rules for main path
        assert findInputRules(rulesOnDstSwitch, dstMainNode).size() == 1
        assert findOutputRules(rulesOnDstSwitch, dstMainNode).size() == 1
        //rule for protected path
        //dst switch contain egress rule for protected, but it looks like ingress
        assert findInputRules(rulesOnDstSwitch, dstProtectedNode).size() == 1
        assert findOutputRules(rulesOnDstSwitch, dstProtectedNode).size() == 0

        if (commonTransitSwitches) {
            commonTransitSwitches.each { sw ->
                def rules = northbound.getSwitchRules(sw).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                assert rules.size() == 4

                def mainNode = mainFlowTransitNodes.find { it.switchId == sw }
                assert findInputRules(rules, mainNode).size() == 1
                assert findOutputRules(rules, mainNode).size() == 1

                def protectedNode = protectedFlowTransitNodes.find { it.switchId == sw }
                assert findInputRules(rules, protectedNode).size() == 1
                assert findOutputRules(rules, protectedNode).size() == 1
            }
        }

        def uniqueTransitNodes = protectedFlowTransitNodes.findAll { !commonNodes.contains(it.switchId) } +
                mainFlowTransitNodes.findAll { !commonNodes.contains(it.switchId) }

        if (uniqueTransitNodes) {
            uniqueTransitNodes.each { node ->
                def rules = northbound.getSwitchRules(node.switchId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                assert rules.size() == 2

                assert findInputRules(rules, node).size() == 1
                assert findOutputRules(rules, node).size() == 1
            }
        }

    }

    static List<FlowEntry> findInputRules(List<FlowEntry> rules, PathNodePayload node) {
        return rules.findAll {
            it.match.inPort == node.inputPort.toString() &&
                    it.instructions.applyActions.flowOutput == node.outputPort.toString()
        }
    }

    static List<FlowEntry> findOutputRules(List<FlowEntry> rules, PathNodePayload node) {
        return rules.findAll {
            it.instructions?.applyActions?.flowOutput == node.inputPort.toString() &&
                    it.match.inPort == node.outputPort.toString()
        }
    }
}
