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

package org.openkilda.service;

import org.openkilda.integration.service.NetworkIntegrationService;
import org.openkilda.model.NetworkPathInfo;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The Class NetworkService.
 *
 * @author Swati Sharma
 */
@Service
public class NetworkService {

    private static final Logger LOGGER = Logger.getLogger(NetworkService.class);

    @Autowired
    private NetworkIntegrationService networkIntegrationService;

    /**
     * Gets the network paths.
     *
     * @param srcSwitch the src switch
     * @param dstSwitch the dst switch
     * @return the network paths
     */
    public NetworkPathInfo getPaths(final String srcSwitch, final String dstSwitch) {
        return networkIntegrationService.getPaths(srcSwitch, dstSwitch);
    }
}
