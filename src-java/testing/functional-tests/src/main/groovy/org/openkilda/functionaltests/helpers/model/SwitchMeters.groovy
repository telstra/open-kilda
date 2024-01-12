package org.openkilda.functionaltests.helpers.model


import org.openkilda.model.FlowMeter
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

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

    void delete(FlowMeter flowMeter) {
        northboundService.deleteMeter(switchId, flowMeter.getMeterId().getValue())
    }
}
