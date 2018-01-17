package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;

/**
 * The Class ServiceSwitchImpl.
 *
 * @author Gaurav Chugh
 */
@Service
public class SwitchService {

    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    /**
     * get All SwitchList.
     *
     * @return SwitchRelationData
     * @throws IntegrationException
     */
    public List<SwitchInfo> getSwitches() throws IntegrationException {
        return switchIntegrationService.getSwitches();
    }

    /**
     * get All Links.
     *
     * @return SwitchRelationData
     * @throws IntegrationException
     */
    public List<IslLinkInfo> getIslLinks() throws IntegrationException {
        return switchIntegrationService.getIslLinks();
    }


    /**
     * get All Ports.
     *
     * @param switchId the switch id
     * @return List<PortInfo>
     * @throws IntegrationException
     */
    public List<PortInfo> getPortsBySwitchId(final String switchId) throws IntegrationException {
        return switchIntegrationService.getSwitchPorts(switchId);
    }
}
