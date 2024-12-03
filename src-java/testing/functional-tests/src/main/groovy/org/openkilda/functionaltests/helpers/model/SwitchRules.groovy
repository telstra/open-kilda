package org.openkilda.functionaltests.helpers.model

import static org.openkilda.model.cookie.Cookie.*
import static org.openkilda.model.cookie.CookieBase.CookieType.*

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.FlowMeter
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH

import groovy.transform.ToString

@ToString(includeNames = true, excludes = 'northboundService, database, cleanupManager')
class SwitchRules {
    NorthboundService northboundService
    Database database
    SwitchId switchId
    CleanupManager cleanupManager
    private static final String COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX =  /-?\d{19}/

    SwitchRules(NorthboundService northboundService,
                Database database,
                CleanupManager cleanupManager,
                SwitchId switchId) {
        this.northboundService = northboundService
        this.database = database
        this.cleanupManager = cleanupManager
        this.switchId = switchId
    }

    List<FlowRuleEntity> forHaFlow(HaFlowExtended haFlow) {
        def haFlowCookies = (database.getHaFlowCookies(haFlow.haFlowId) + database.getHaSubFlowsCookies(haFlow.subFlows*.haSubFlowId))
                .collect {it.getValue()}
        def switchRules = getRules()
        return switchRules.findAll {haFlowCookies.contains(it.getCookie())}
    }

    Set<FlowRuleEntity> relatedToMeter(FlowMeter flowMeter) {
        return getRules().findAll {it.getInstructions().getGoToMeter() == flowMeter.getMeterId().getValue()}
    }

    List<Long> install(InstallRulesAction installAction) {
        northboundService.installSwitchRules(switchId, installAction)
    }

    RulesValidationResult validate() {
        northboundService.validateSwitchRules(switchId)
    }

    RulesSyncResult synchronize() {
        northboundService.synchronizeSwitchRules(switchId)
    }

    void delete(FlowRuleEntity flowEntry) {
        delete(flowEntry.getCookie())
    }

    List<Long> delete(long cookie) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northboundService.synchronizeSwitch(switchId, true)})
        northboundService.deleteSwitchRules(switchId, cookie)
    }

    List<Long> delete(DeleteRulesAction deleteAction) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northboundService.synchronizeSwitch(switchId, true)})
        return northboundService.deleteSwitchRules(switchId, deleteAction)
    }

    List<Long> delete(Integer inPort, Integer inVlan, String encapsulationType,
                      Integer outPort) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northboundService.synchronizeSwitch(switchId, true)})
        return northboundService.deleteSwitchRules(switchId, inPort, inVlan, encapsulationType, outPort)
    }

    List<Long> delete(int priority) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northboundService.synchronizeSwitch(switchId, true)})
        return northboundService.deleteSwitchRules(switchId, priority)
    }

    static Set<Long> missingRuleCookieIds(Collection<PathDiscrepancyDto> missingRules) {
        return missingRules.collect {new Long((it.getRule() =~ COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX)[0])}
    }

    FlowRuleEntity pingRule(String encapsulationType) {
        def pingRuleCookie = getPingRuleCookie(encapsulationType)
        return getRules().find { it.cookie == pingRuleCookie }
    }

    static long getPingRuleCookie(String encapsulationType) {
        if (FlowEncapsulationType.TRANSIT_VLAN.toString().equalsIgnoreCase(encapsulationType)) {
            return VERIFICATION_UNICAST_RULE_COOKIE
        } else if (FlowEncapsulationType.VXLAN.toString().equalsIgnoreCase(encapsulationType)) {
            return VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
        } else {
            throw new IllegalArgumentException("Unknown encapsulation " + encapsulationType)
        }
    }

    List<FlowRuleEntity> getRulesWithMeter() {
        return getRules().findAll {
            !new Cookie(it.cookie).serviceFlag && it.instructions.goToMeter
        }
    }

    List<FlowRuleEntity> getRulesByCookieType(CookieType cookieType) {
       getRules().findAll { new Cookie(it.cookie).getType() == cookieType }
    }

    List<FlowRuleEntity> getServer42FlowRelatedRules() {
        getRules().findAll { new Cookie(it.cookie).getType() in [SERVER_42_FLOW_RTT_INPUT, SERVER_42_FLOW_RTT_INGRESS] }
    }

    List<FlowRuleEntity> getServer42ISLRelatedRules() {
        getRules().findAll { (new Cookie(it.cookie).getType() in [SERVER_42_ISL_RTT_INPUT] ||
                it.cookie in [SERVER_42_ISL_RTT_TURNING_COOKIE, SERVER_42_ISL_RTT_OUTPUT_COOKIE]) }
    }

    List<FlowRuleEntity> getServer42SwitchRelatedRules() {
        getRules().findAll { it.cookie in [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE,
                                           SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE] }
    }

    List<FlowRuleEntity> getRules() {
        northboundService.getSwitchRules(switchId).flowEntries.collect { new FlowRuleEntity(it) }
    }
}
