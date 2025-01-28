package org.openkilda.functionaltests.helpers.model

import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.isMeterIdOfDefaultRule

import org.openkilda.messaging.info.meter.MeterEntry
import org.openkilda.model.FlowMeter
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.switches.DeleteMeterResult
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.ToString

@ToString(includeNames = true, excludes = 'northboundService, database')
class SwitchMeters {
    NorthboundService northboundService
    Database database
    SwitchId switchId

    SwitchMeters(NorthboundService northboundService,
                 Database database,
                 SwitchId switchId) {
        this.northboundService = northboundService
        this.database = database
        this.switchId = switchId
    }

    Set<FlowMeter> forHaFlow(HaFlowExtended haFlow) {
        return database.getHaFlowMeters(haFlow.subFlows, haFlow.haFlowId)
                .findAll {it.getSwitchId() == switchId}
    }

    List<MeterEntry> getMeters() {
        northboundService.getAllMeters(switchId).meterEntries
    }

    List<Long> getCreatedMeterIds() {
        return getMeters().findAll { it.meterId > MAX_SYSTEM_RULE_METER_ID }*.meterId
    }

    List<Long> getNotDefaultMeters() {
        getMeters().findAll { !isMeterIdOfDefaultRule(it.meterId) }.meterId
    }

    DeleteMeterResult delete(FlowMeter flowMeter) {
        delete(flowMeter.getMeterId().getValue())
    }

    DeleteMeterResult delete(Long meterId) {
        northboundService.deleteMeter(switchId, meterId)
    }
}
