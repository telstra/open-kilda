package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
public class ServiceSwitch {

    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    /**
     * get All SwitchList.
     *
     * @return SwitchRelationData
     */
    public List<SwitchInfo> getSwitches() {
        return switchIntegrationService.getSwitches();
    }

    /**
     * get All Links.
     *
     * @return SwitchRelationData
     */
    public List<IslLinkInfo> getIslLinks() {
        return switchIntegrationService.getIslLinks();
    }


    /**
     * get All Ports.
     *
     * @param switchId the switch id
     * @return List<PortInfo>
     */
    public List<PortInfo> getPortDetailBySwitchId(final String switchId) {
        return switchIntegrationService.getSwitchPorts(switchId);
    }
}
