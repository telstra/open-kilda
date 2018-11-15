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

package org.openkilda.controller;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchInfo;
import org.openkilda.service.SwitchService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Class SwitchController.
 *
 * @author sumitpal.singh
 *
 */

@RestController
@RequestMapping(value = "/api/switch")
public class SwitchController  {

    @Autowired
    private SwitchService serviceSwitch;

    @Autowired
    private ActivityLogger activityLogger;


    /**
     * Gets the switches detail.
     *
     * @return the switches detail
     */
    @RequestMapping(value = "/list")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<SwitchInfo> getSwitchesDetail() {
        return serviceSwitch.getSwitches();
    }


    /**
     * Gets the links detail.
     *
     * @return the links detail
     */
    @RequestMapping(value = "/links", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<IslLinkInfo> getLinksDetail() {
        return serviceSwitch.getIslLinks();
    }

    /**
     * Gets the link props.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the link props
     */
    @RequestMapping(path = "/link/props", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody LinkProps getLinkProps(@RequestParam(value = "src_switch", required = true) String srcSwitch,
            @RequestParam(value = "src_port", required = true) String srcPort,
            @RequestParam(value = "dst_switch", required = true) String dstSwitch,
            @RequestParam(value = "dst_port", required = true) String dstPort) {
        return serviceSwitch.getLinkProps(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Get Link Props.
     *
     * @param keys the link properties
     * @return the link properties string
     */
    @RequestMapping(path = "/link/props", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String updateLinkProps(@RequestBody final List<LinkProps> keys) {
        LinkProps props = (keys != null && !keys.isEmpty()) ? keys.get(0) : null;
        String key = props != null ? "Src_SW_" + props.getSrcSwitch() + "\nSrc_PORT_" + props.getSrcPort() + "\nDst_SW_"
                + props.getDstSwitch() + "\nDst_PORT_" + props.getDstPort() + "\nCost_" + props.getProperty("cost")
                : "";
        activityLogger.log(ActivityType.ISL_UPDATE_COST, key);
        return serviceSwitch.updateLinkProps(keys);
    }

    /**
     * Get Switch Rules.
     *
     * @param switchId the switch id
     * @return  the switch rules
     */
    @RequestMapping(path = "/{switchId}/rules", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String getSwitchRules(@PathVariable final String switchId) {
        activityLogger.log(ActivityType.SWITCH_RULES, switchId);
        return serviceSwitch.getSwitchRules(switchId);
    }
    
    /**
     * Configure switch port.
     *
     * @param configuration the configuration
     * @param switchId the switch id
     * @param port the port
     * @return the configuredPort
     */
    @RequestMapping(path = "/{switchId}/{port}/config", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_PORT_CONFIG})
    public @ResponseBody ConfiguredPort configureSwitchPort(@RequestBody final PortConfiguration configuration,
            @PathVariable final String switchId, @PathVariable final String port) {
        activityLogger.log(ActivityType.CONFIGURE_SWITCH_PORT, "SW_" + switchId + ", P_" + port);
        return serviceSwitch.configurePort(switchId, port, configuration);
    }
}
