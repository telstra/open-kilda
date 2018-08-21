package org.openkilda.service;

import java.util.List;

import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public List<IslLinkInfo> getIslLinks() {
        return switchIntegrationService.getIslLinks();
    }


    /**
     * Get link props.
     * 
     * @param keys
     * @return
     */
    public LinkProps getLinkProps(LinkProps keys) {
        List<LinkProps> linkPropsList = switchIntegrationService.getIslLinkProps(keys);
        return (linkPropsList != null && !linkPropsList.isEmpty()) ? linkPropsList.get(0) : null;
    }

    /**
     * Update link props.
     * 
     * @param keys
     * @return
     */
    public String updateLinkProps(List<LinkProps> keys) {
        return switchIntegrationService.updateIslLinkProps(keys);
    }

    /**
     * Get Switch Rules.
     * 
     * @param switchId
     * @return
     */
    public String getSwitchRules(String switchId) {
        return switchIntegrationService.getSwitchRules(switchId);
    }
    
    /**
     * Configure port.
     *
     * @param switchId the switch id
     * @param port the port
     * @param configuration the configuration
     * @return the configuredPort
     */
    public ConfiguredPort configurePort(String switchId, String port, PortConfiguration configuration) {
        return switchIntegrationService.configurePort(switchId, port, configuration);
    }
}
