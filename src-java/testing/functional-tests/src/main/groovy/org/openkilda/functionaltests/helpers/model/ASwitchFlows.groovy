package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.springframework.beans.factory.annotation.Autowired

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_A_SWITCH_FLOWS
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component


@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
class ASwitchFlows {
    @Autowired
    LockKeeperService lockKeeperService
    @Autowired
    CleanupManager cleanupManager

    void addFlows(List<ASwitchFlow> flows) {
        cleanupManager.addAction(RESTORE_A_SWITCH_FLOWS, {lockKeeperService.removeFlows(flows)})
        lockKeeperService.addFlows(flows)
    }

    void removeFlows(List<ASwitchFlow> flows) {
        cleanupManager.addAction(RESTORE_A_SWITCH_FLOWS, {lockKeeperService.addFlows(flows)})
        lockKeeperService.removeFlows(flows)
    }

    List<ASwitchFlow> getAllFlows() {
        return lockKeeperService.getAllFlows()
    }

}
