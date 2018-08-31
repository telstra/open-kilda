/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.service;

import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * @return SwitchRelationData the switch info
     * @throws IntegrationException the integration exception
     */
    public List<SwitchInfo> getSwitches() throws IntegrationException {
        return switchIntegrationService.getSwitches();
    }

    /**
     * get All Links.
     *
     * @return SwitchRelationData the isl link info
     * @throws IntegrationException the integration exception
     */
    public List<IslLinkInfo> getIslLinks() {
        return switchIntegrationService.getIslLinks();
    }


    /**
     * Get link props.
     * 
     * @param keys the link properties
     * @return the link properties
     */
    public LinkProps getLinkProps(LinkProps keys) {
        List<LinkProps> linkPropsList = switchIntegrationService.getIslLinkProps(keys);
        return (linkPropsList != null && !linkPropsList.isEmpty()) ? linkPropsList.get(0) : null;
    }

    /**
     * Update link props.
     * 
     * @param keys the link properties
     * @return the link properties
     */
    public String updateLinkProps(List<LinkProps> keys) {
        return switchIntegrationService.updateIslLinkProps(keys);
    }

    /**
     * Get Switch Rules.
     * 
     * @param switchId the switch id
     * @return the switch rules
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
