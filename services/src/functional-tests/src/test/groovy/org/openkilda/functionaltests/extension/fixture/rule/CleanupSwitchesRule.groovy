package org.openkilda.functionaltests.extension.fixture.rule

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.northbound.dto.switches.RulesValidationResult
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.InheritConstructors
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
        topology.activeSwitches.each { sw ->
            try {
                Wrappers.wait(WAIT_OFFSET) {
                    def rules = northbound.validateSwitchRules(sw.dpId)
                    rules.excessRules.empty ?: failWith(new ExcessRulesException(
                            "Switch $sw.dpId has excess rules at the end of the test.", rules))
                    rules.properRules.empty ?: failWith(new ProperRulesException(
                            "FATAL: there are proper rules left after the test. This usually means" +
                                    " that a certain flow was not properly removed from database and Kilda is in an" +
                                    " inconsistent state.\nSwitch: $sw.dpId", rules))
                    rules.missingRules.empty ?: failWith(new MissingRulesException(
                            "FATAL: there are missing rules left after the test. This usually means" +
                                    " that a certain flow was not properly removed from database and Kilda is in an" +
                                    " inconsistent state.\nSwitch: $sw.dpId", rules))
                }
            } catch (WaitTimeoutException e) {
                //TODO(rtretiak): Not able to deal with randomly appearing rules from previous tests at this point.
                //Waiting for new Kilda patch with Hub&Spoke features
                def originalError = e.originalError
                switch (originalError) {
                    case ExcessRulesException:
                        log.warn(originalError.message + "\nForcing switch cleanup.")
                        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
                        break
                    case MissingRulesException:
                    case ProperRulesException:
                        //we are unable to deal with this. Kilda is inconsistent.
                        throw originalError
                    default:
                        throw originalError

                }
            }
        }
    }

    static void failWith(Throwable t) {
        throw t
    }

    private static abstract class RulesException extends RuntimeException {
        RulesValidationResult rules

        RulesException(String message, RulesValidationResult rules) {
            super(message + "\nRules: $rules")
            this.rules = rules
        }
    }

    @InheritConstructors
    private static class ExcessRulesException extends RulesException {}

    @InheritConstructors
    private static class MissingRulesException extends RulesException {}

    @InheritConstructors
    private static class ProperRulesException extends RulesException {}
}
