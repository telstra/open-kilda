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

import org.openkilda.constants.IConstants;
import org.openkilda.integration.source.store.FlowStoreService;
import org.openkilda.integration.source.store.dto.Contract;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.util.List;

@Service
public class ContractService {

    @Autowired
    private FlowStoreService flowStoreService;

    @Autowired
    private UserService userService;

    private static final Logger LOGGER = Logger.getLogger(ContractService.class);

    /**
     * get contracts.
     *
     * @param linkId
     *            the link id
     */
    public List<Contract> getContracts(String linkId) {
        LOGGER.info("Inside ContractService method getContracts");
        UserInfo userInfo = userService.getLoggedInUserInfo();
        if (userInfo.getPermissions().contains(IConstants.Permission.FW_FLOW_INVENTORY)) {
            if (userInfo.getPermissions().contains(IConstants.Permission.FW_FLOW_CONTRACT)) {
                List<Contract> contracts = flowStoreService.getContracts(linkId);
                return contracts;
            }
        }
        return null;
    }

    /**
     * Delete contract.
     *
     * @param linkId the link id
     * @param contractid the contractid
     * @return true, if successful
     */
    public boolean deleteContract(String linkId, String contractid) {
        LOGGER.info("Inside ContractService method deleteContract");
        flowStoreService.deleteContract(linkId, contractid);
        return true;
    }
}
