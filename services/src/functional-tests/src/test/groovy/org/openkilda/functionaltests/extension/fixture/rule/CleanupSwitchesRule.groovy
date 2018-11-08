package org.openkilda.functionaltests.extension.fixture.rule

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

/**
 * See {@link CleanupSwitches}
 */
//TODO(rtretiak): This almost doubles the test execution time. Consider removing when Kilda gets more stable
@Slf4j
class CleanupSwitchesRule extends AbstractRuleExtension<CleanupSwitches> {

    @Autowired
    NorthboundService northbound
    @Autowired
    TopologyDefinition topology

    @Override
    void afterTest() {
        log.debug "Verifying that all the switches are clean"
        try {
            Wrappers.wait(WAIT_OFFSET) {
                topology.activeSwitches.each { sw ->
                    def rules = northbound.validateSwitchRules(sw.dpId)
                    assert rules.missingRules.empty
                    assert rules.excessRules.empty
                    assert rules.properRules.empty
                }
            }
        } catch (WaitTimeoutException e) {
            //TODO(rtretiak): Not able to deal with randomly appearing rules from previous tests at this point.
            //Waiting for new Kilda patch with Hub&Spoke features
            log.warn("Switches do not appear to be cleaned at the end of the test. Force cleaning switches." +
                    "\nOriginal error:\n$e")
            topology.activeSwitches.each { sw ->
                northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
            }
        }
    }
}
