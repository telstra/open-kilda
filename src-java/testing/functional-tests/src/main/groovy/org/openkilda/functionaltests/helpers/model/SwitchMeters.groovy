package org.openkilda.functionaltests.helpers.model


import org.openkilda.model.FlowMeter
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

class SwitchMeters {
    NorthboundService northboundService
    NorthboundServiceV2 northboundServiceV2
    Database database
    SwitchId switchId

    SwitchMeters(NorthboundService northboundService,
                 NorthboundServiceV2 northboundServiceV2,
                 Database database,
                 SwitchId switchId) {
        this.northboundService = northboundService
        this.northboundServiceV2 = northboundServiceV2
        this.database = database
        this.switchId = switchId
    }

    Set<FlowMeter> forHaFlow(HaFlow haFlow) {
        return database.getHaFlowMeters(haFlow)
                .findAll {it.getSwitchId() == switchId}
    }

    void delete(FlowMeter flowMeter) {
        northboundService.deleteMeter(switchId, flowMeter.getMeterId().getValue())
    }
}
