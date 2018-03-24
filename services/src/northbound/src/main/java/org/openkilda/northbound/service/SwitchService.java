package org.openkilda.northbound.service;

import org.openkilda.messaging.command.switches.DefaultRulesAction;
import org.openkilda.northbound.dto.SwitchDto;

import java.util.List;

public interface SwitchService extends BasicService {

    List<SwitchDto> getSwitches();

    /**
     * Deletes all rules from the switch. The flag (@code defaultRules) defines what to do about the default rules.
     *
     * @param switchId switch id
     * @param defaultRules defines what to do about the default rules
     * @param correlationId request correlation Id
     * @return the list of cookies for removed rules
     */
    List<Long> deleteRules(String switchId, DefaultRulesAction defaultRules, String correlationId);
}
