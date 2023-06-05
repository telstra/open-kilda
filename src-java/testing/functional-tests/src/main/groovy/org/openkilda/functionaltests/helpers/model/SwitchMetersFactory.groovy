package org.openkilda.functionaltests.helpers.model

import org.openkilda.model.SwitchId
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class SwitchMetersFactory {
    @Autowired
    @Qualifier("northboundServiceImpl")
    NorthboundService northboundService

    @Autowired
    Database database

    SwitchMeters get(SwitchId switchId) {
        return new SwitchMeters(northboundService, database, switchId)
    }
}
