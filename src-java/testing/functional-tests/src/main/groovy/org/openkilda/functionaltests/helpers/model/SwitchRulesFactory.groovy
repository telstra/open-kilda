package org.openkilda.functionaltests.helpers.model

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchId
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope(SCOPE_PROTOTYPE)
class SwitchRulesFactory {
    @Autowired
    @Qualifier("northboundServiceImpl")
    NorthboundService northboundService
    @Autowired
    CleanupManager cleanupManager

    @Autowired
    Database database

    SwitchRules get(SwitchId switchId) {
        return new SwitchRules(northboundService, database, cleanupManager, switchId)
    }
}
