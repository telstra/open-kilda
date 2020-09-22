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

package org.openkilda.saml.controller;

import org.openkilda.controller.BaseController;
import org.openkilda.saml.model.SamlConfig;
import org.openkilda.saml.service.SamlService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.usermanagement.model.Message;

import java.util.List;

/**
 * The Class SamlConfigController.
 *
 * @author Swati Sharma
 */

@RestController
@RequestMapping(value = "/api/samlconfig")
@ComponentScan
public class SamlConfigController extends BaseController {

    @Autowired
    private SamlService samlService;

    /**
     * Creates the provider.
     *
     * @param file the metadata file
     * @param name the provider name
     * @param url the metadata url
     * @param entityId the entityId
     * @param status the provider status
     * @param attribute the attribute
     * @param userCreation the userCreation
     * @param roleIds the role Ids
     * @return the SamlConfig
     */
    @PostMapping
    public SamlConfig create(@RequestParam(name = "file", required = false) MultipartFile file, 
            @RequestParam(name = "name", required = true) String name, 
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "entity_id", required = true) String entityId,
            @RequestParam(name = "status", required = true) boolean status,
            @RequestParam(name = "attribute", required = true) String attribute,
            @RequestParam(name = "user_creation", required = true) boolean userCreation,
            @RequestParam(name = "role_ids", required = false) List<Long> roleIds) {
        SamlConfig samlConfig = samlService.create(file, name, url, entityId, status, 
                userCreation, roleIds, attribute);
        return samlConfig;
    }
    
    /**
     * Updates the provider.
     *
     * @param file the metadata file
     * @param url the metadata url
     * @param name the provider name
     * @param entityId the entityId
     * @param status the provider status
     * @param attribute the attribute
     * @param userCreation the userCreation
     * @param roleIds the role Ids
     * @return the SamlConfig
     */
    @RequestMapping(value = "/{uuid}", method = RequestMethod.PATCH)
    public SamlConfig update(@PathVariable("uuid") String uuid, 
            @RequestParam(name = "file", required = false) MultipartFile file, 
            @RequestParam(name = "name", required = true) String name, 
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "entity_id", required = false) String entityId,
            @RequestParam(name = "status", required = true) boolean status,
            @RequestParam(name = "attribute", required = true) String attribute,
            @RequestParam(name = "user_creation", required = true) boolean userCreation,
            @RequestParam(name = "role_ids", required = false) List<Long> roleIds) {
        SamlConfig configResponse = samlService.update(uuid, file, name, url, entityId, status, 
                attribute, userCreation, roleIds);
        return configResponse;
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{uuid}", method = RequestMethod.GET)
    public SamlConfig getById(@PathVariable("uuid") final String uuid) {
        SamlConfig configResponse = samlService.getById(uuid);
        return configResponse;
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{uuid}", method = RequestMethod.DELETE)
    public Message delete(@PathVariable("uuid") final String uuid) {
        return samlService.deleteByUuid(uuid);
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<SamlConfig> getAll() {
        return samlService.getAll();
    }
    
}
