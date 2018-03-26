package org.openkilda.northbound.service;

import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.northbound.dto.SwitchDto;

import java.util.List;

public interface SwitchService extends BasicService {

    List<SwitchDto> getSwitches();

    /**
     * Deletes all rules from the switch. The flag (@code deleteAction) defines what to do about the default rules.
     *
     * @param switchId switch id
     * @param deleteAction defines what to do about the default rules
     * @param cookie if defaultRules is ONE, then the cookie
     * @param correlationId request correlation Id
     * @return the list of cookies for removed rules
     */
    List<Long> deleteRules(String switchId, DeleteRulesAction deleteAction, Long cookie, String correlationId);

    /**
     * Install default rules on the switch. The flag (@code installAction) defines what to do about the default rules.
     *
     * @param switchId switch id
     * @param installAction defines what to do about the default rules
     * @param correlationId request correlation Id
     * @return the list of cookies for installed rules
     */
    List<Long> installRules(String switchId, InstallRulesAction installAction, String correlationId);


    /**
     * Sets (or just gets) the connection mode for the switches. The connection mode governs the
     * policy for what Floodlight does.
     *
     * @param mode the mode to use. If null, then just return existing value.
     * @param correlationId request correlation Id
     * @return the value of connection mode after the operation
     */
    ConnectModeRequest.Mode connectMode(ConnectModeRequest.Mode mode, String correlationId);
}
