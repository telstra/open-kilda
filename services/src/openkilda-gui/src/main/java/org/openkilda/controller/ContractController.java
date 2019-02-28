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

import org.openkilda.integration.source.store.dto.Contract;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.service.ContractService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@Controller
@RequestMapping(value = "/api/contracts")
public class ContractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractController.class);

    @Autowired
    private ContractService contractService;
    
    @Autowired
    private ActivityLogger activityLogger;

    /**
     * Returns contracts exists in the system.
     *
     * @return contracts exists in the system.
     */
    @RequestMapping(value = "/list/{linkId}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<Contract> getAllContracts(
            @PathVariable(name = "linkId", required = false) String linkId) {
        LOGGER.info("Get all contracts. Flow id: '" + linkId + "'");
        return contractService.getContracts(linkId);
    }

    /**
     * Returns delete contract exists in the system.
     */
    @RequestMapping(value = "/delete/{flowId}/{contractid:.+}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public boolean deleteContract(@PathVariable(name = "flowId", required = false) String flowId,
            @PathVariable(name = "contractid", required = false) String contractid) {
        LOGGER.info("Delete contract. Flow id: '" + flowId + "' Contractid id: '" + contractid + "'");
        activityLogger.log(ActivityType.DELETE_CONTRACT, "flowid:" + flowId + ",\ncontractid:" + contractid);
        return contractService.deleteContract(flowId, contractid);
    }
}
