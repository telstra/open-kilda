/* Copyright 2019 Telstra Open Source
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

import org.openkilda.model.NetworkPathInfo;
import org.openkilda.service.NetworkService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Class NetworkController.
 *
 * @author Swati Sharma
 */

@RestController
@RequestMapping(value = "/api/network")
public class NetworkController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkController.class);
    
    @Autowired
    private NetworkService networkService;

    /**
     * Gets the network paths.
     *
     * @param srcSwitch the src switch
     * @param dstSwitch the dst switch
     * @return the network paths
     */
    @RequestMapping(value = "/paths", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody NetworkPathInfo getNetworkPathDetail(@RequestParam(value = "src_switch", required = true) 
                                                              final String srcSwitch, 
                                                              @RequestParam(value = "dst_switch", required = true) 
                                                              final String dstSwitch) {
        return networkService.getPaths(srcSwitch, dstSwitch);
    }
}
