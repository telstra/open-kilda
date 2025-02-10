package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.FlowMeter
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH

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

    Set<FlowRuleEntity> relatedToMeter(FlowMeter flowMeter) {
        return getRules().findAll {it.getInstructions().getGoToMeter() == flowMeter.getMeterId().getValue()}
    }

    void delete(FlowRuleEntity flowEntry) {
        delete(flowEntry.getCookie())
    }

    void delete(long cookie) {
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {northboundService.synchronizeSwitch(switchId, true)})
        northboundService.deleteSwitchRules(switchId, cookie)
    }

    static Set<Long> missingRuleCookieIds(Collection<PathDiscrepancyDto> missingRules) {
        return missingRules.collect {Long.valueOf((it.getRule() =~ COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX)[0])}
    }

    FlowRuleEntity pingRule(String encapsulationType) {
        def pingRuleCookie = getPingRuleCookie(encapsulationType)
        return getRules().find { it.cookie == pingRuleCookie }
    }

    static long getPingRuleCookie(String encapsulationType) {
        if (FlowEncapsulationType.TRANSIT_VLAN.toString().equalsIgnoreCase(encapsulationType)) {
            return Cookie.VERIFICATION_UNICAST_RULE_COOKIE
        } else if (FlowEncapsulationType.VXLAN.toString().equalsIgnoreCase(encapsulationType)) {
            return Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
        } else {
            throw new IllegalArgumentException("Unknown encapsulation " + encapsulationType)
        }
    }

    List<FlowRuleEntity> getRulesByCookieType(CookieType cookieType) {
       getRules().findAll { new Cookie(it.cookie).getType() == cookieType }
    }

    List<FlowRuleEntity> getServer42FlowRules() {
        getRules().findAll { new Cookie(it.cookie).getType() in [CookieType.SERVER_42_FLOW_RTT_INPUT,
                                                               CookieType.SERVER_42_FLOW_RTT_INGRESS] }
    }

    List<FlowRuleEntity> getServer42ISLRules() {
        getRules().findAll { (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT] ||
                it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) }
    }

    List<FlowRuleEntity> getRules() {
        northboundService.getSwitchRules(switchId).flowEntries.collect { new FlowRuleEntity(it) }
    }
}
