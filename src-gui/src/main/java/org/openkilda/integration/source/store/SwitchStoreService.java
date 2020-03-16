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

package org.openkilda.integration.source.store;

import org.openkilda.integration.auth.service.IAuthService;
import org.openkilda.integration.exception.StoreIntegrationException;
import org.openkilda.integration.source.store.dto.InventorySwitch;
import org.openkilda.integration.source.store.dto.Port;
import org.openkilda.store.common.constants.RequestParams;
import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.constants.Url;
import org.openkilda.store.model.AuthConfigDto;
import org.openkilda.store.model.Customer;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.AuthService;
import org.openkilda.store.service.StoreService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SwitchStoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowStoreService.class);

    @Autowired
    private StoreService storeService;

    @Autowired
    private AuthService authService;

    /**
     * Gets the switches with params.
     *
     * @return the switches with params
     */
    public List<InventorySwitch> getSwitches() {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.SWITCH_STORE, Url.GET_ALL_SWITCHES);
            AuthConfigDto authDto = authService.getAuth(StoreType.SWITCH_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponseList(urlDto, authDto, InventorySwitch.class);
        } catch (Exception e) {
            LOGGER.error("Error occurred while retriving switches", e);
            throw new StoreIntegrationException(e);
        }
    }
    
    /**
     * Gets the customer flows.
     *
     * @return the customer with flows
     */
    public List<Customer> getPortFlows(String switchId, String port) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.SWITCH_STORE, Url.GET_SWITCH_PORT_FLOWS);
            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.SWITCH_ID.getName(), switchId);
            params.put(RequestParams.PORT_NUMBER.getName(), port);
            urlDto.setParams(params);
            AuthConfigDto authDto = authService.getAuth(StoreType.SWITCH_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponseList(urlDto, authDto, Customer.class);
        } catch (Exception e) {
            LOGGER.error(
                    "Error occurred while retriving switch port flows. Switch Id: " + switchId + ", Port: " + port,
                    e);
            throw new StoreIntegrationException(e);
        }
    }
    
    /**
     * Gets the switch port.
     *
     * @param switchId the switch id
     * @return the switch port
     */
    public List<Port> getSwitchPort(final String switchId) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.SWITCH_STORE, Url.GET_SWITCH_PORTS);
            AuthConfigDto authDto = authService.getAuth(StoreType.SWITCH_STORE);
            
            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.SWITCH_ID.getName(), switchId);

            urlDto.setParams(params);
            
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponseList(urlDto, authDto, Port.class);
        } catch (Exception e) {
            LOGGER.error("Error occurred while retriving switch ports. Switch Id: " + switchId, e);
            throw new StoreIntegrationException(e);
        }
    }
    
    
    /**
     * Gets the switch with params.
     *
     * @return the switch with params
     */
    public InventorySwitch getSwitch(String switchId) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.SWITCH_STORE, Url.GET_SWITCH);
            
            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.SWITCH_ID.getName(), switchId);

            urlDto.setParams(params);
            AuthConfigDto authDto = authService.getAuth(StoreType.SWITCH_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponse(urlDto, authDto, InventorySwitch.class);
        } catch (Exception e) {
            LOGGER.error("Error occurred while retriving switches. Switch Id: " + switchId, e);
            throw new StoreIntegrationException(e);
        }
    }
}
