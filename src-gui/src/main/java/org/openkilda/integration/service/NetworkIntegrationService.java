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

package org.openkilda.integration.service;

import org.openkilda.constants.IConstants;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.converter.FlowConverter;
import org.openkilda.integration.converter.FlowPathConverter;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.model.NetworkPathInfo;
import org.openkilda.model.Path;
import org.openkilda.service.ApplicationService;
import org.openkilda.utility.ApplicationProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * The Class NetworkIntegrationService.
 *
 * @author Swati Sharma
 */

@Service
public class NetworkIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkIntegrationService.class);

    @Autowired
    private RestClientManager restClientManager;

    @Autowired
    private FlowPathConverter flowPathConverter;

    @Autowired
    private FlowConverter flowConverter;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    /**
     * Gets the paths.
     *
     * @return the paths
     */
    public NetworkPathInfo getPaths(String srcSwitch, String dstSwitch) {

        NetworkPathInfo networkPath = getNetworkPath(srcSwitch, dstSwitch);
        if (networkPath != null) {
            List<Path> pathList = networkPath.getPaths();
            pathList.forEach(path -> {
                path.getNode().forEach(node -> {
                    node.setSwitchName(switchIntegrationService
                                    .customSwitchName(switchIntegrationService.getSwitchNames(), node.getSwitchId()));
                });
            });
            return networkPath;
        }
        return null;
    }
    
    /**
     * Gets the network path.
     *
     * @return the network path
     */
    public NetworkPathInfo getNetworkPath(final String srcSwitch, final String dstSwitch) {
        try {
            HttpResponse response = restClientManager.invoke(
                     applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_NETWORK_PATH
                            .replace("{src_switch}", srcSwitch).replace("{dst_switch}", dstSwitch),
                     HttpMethod.GET, "", "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, NetworkPathInfo.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting network path", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }
}
