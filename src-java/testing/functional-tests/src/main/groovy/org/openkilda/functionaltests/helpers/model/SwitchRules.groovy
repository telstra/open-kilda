package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.model.FlowMeter
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService


class SwitchRules {
    NorthboundService northboundService
    Database database
    SwitchId switchId
    private static final String COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX =  /-?\d{19}/

    SwitchRules(NorthboundService northboundService,
                Database database,
                SwitchId switchId) {
        this.northboundService = northboundService
        this.database = database
        this.switchId = switchId
    }

    List<FlowEntry> forHaFlow(HaFlow haFlow) {
        def haFlowCookies = (database.getHaFlowCookies(haFlow.getHaFlowId()) + database.getHaSubFlowsCookies(haFlow))
                .collect {it.getValue()}
        def switchRules = northboundService.getSwitchRules(switchId)
        return switchRules.getFlowEntries().findAll {haFlowCookies.contains(it.getCookie())}
    }

    Set<FlowEntry> relatedToMeter(FlowMeter flowMeter) {
        return northboundService.getSwitchRules(switchId).getFlowEntries()
                .findAll {it.getInstructions().getGoToMeter() == flowMeter.getMeterId().getValue()}
    }

    void delete(FlowEntry flowEntry) {
        northboundService.deleteSwitchRules(switchId, flowEntry.getCookie())
    }

    static Set<Long> missingRuleCookieIds(Collection<PathDiscrepancyDto> missingRules) {
        return missingRules.collect {new Long((it.getRule() =~ COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX)[0])}
    }
}
