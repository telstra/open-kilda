package org.openkilda.northbound.service;

import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.northbound.dto.SwitchDto;
import org.openkilda.northbound.dto.switches.RulesValidationResult;

import java.util.List;

public interface SwitchService extends BasicService {

    List<SwitchDto> getSwitches();

    /**
     * Get all rules from the switch. If cookie is specified, then return just that cookie rule.
     *
     * @param switchId the switch
     * @param cookie if > 0, then filter the results based on that cookie
     * @return the list of rules
     */
    SwitchFlowEntries getRules(String switchId, Long cookie);

    /**
     * Get all rules from the switch. If cookie is specified, then return just that cookie rule.
     *
     * @param switchId the switch
     * @param cookie if > 0, then filter the results based on that cookie
     * @param correlationId passed correlation id
     * @return the list of rules
     */
    SwitchFlowEntries getRules(String switchId, Long cookie, String correlationId);

    /**
     * Deletes all rules from the switch. The flag (@code deleteAction) defines what to do about the default rules.
     *
     * @param switchId switch id
     * @param deleteAction defines what to do about the default rules
     * @param cookie if defaultRules is ONE, then the cookie
     * @return the list of cookies for removed rules
     */
    List<Long> deleteRules(String switchId, DeleteRulesAction deleteAction, Long cookie);

    /**
     * Install default rules on the switch. The flag (@code installAction) defines what to do about the default rules.
     *
     * @param switchId switch id
     * @param installAction defines what to do about the default rules
     * @return the list of cookies for installed rules
     */
    List<Long> installRules(String switchId, InstallRulesAction installAction);


    /**
     * Sets (or just gets) the connection mode for the switches. The connection mode governs the
     * policy for what Floodlight does.
     *
     * @param mode the mode to use. If null, then just return existing value.
     * @return the value of connection mode after the operation
     */
    ConnectModeRequest.Mode connectMode(ConnectModeRequest.Mode mode);

    /**
     * Validate the rules installed on the switch against the flows in Neo4J.
     *
     * @param switchId switch to validate rules on.
     * @return the validation details.
     */
    RulesValidationResult validateRules(String switchId);

    /**
     * Synchronize (install) missing flows that should be on the switch but exist only in Neo4J.
     *
     * @param switchId switch to synchronize rules on.
     * @return the synchronization result.
     */
    RulesSyncResult syncRules(String switchId);
}
