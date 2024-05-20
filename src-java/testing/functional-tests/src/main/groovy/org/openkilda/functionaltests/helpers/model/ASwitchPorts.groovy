package org.openkilda.functionaltests.helpers.model

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
class ASwitchPorts {
    @Autowired
    LockKeeperService lockKeeperService
    @Autowired
    CleanupManager cleanupManager

    void setDown(List<Integer> portsToSetDown) {
        cleanupManager.addAction(RESTORE_ISL, {lockKeeperService.portsUp(portsToSetDown)})
        lockKeeperService.portsDown(portsToSetDown)
    }

    void setUp(List<Integer> portsToSetUp) {
        lockKeeperService.portsUp(portsToSetUp)
    }

    List<ASwitchFlow> getAllFlows() {
        return lockKeeperService.getAllFlows()
    }

}
