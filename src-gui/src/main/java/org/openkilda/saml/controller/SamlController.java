/* Copyright 2020 Telstra Open Source
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
import org.openkilda.saml.model.SamlConfigResponse;
import org.openkilda.saml.service.SamlService;

import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 * The Class SamlController.
 *
 * @author Swati Sharma
 */

@RestController
@RequestMapping(value = "/api/samlconfig")
@ComponentScan
public class SamlController extends BaseController {

    @Autowired
    private SamlService idpService;

    private static final Logger LOGGER = LoggerFactory.getLogger(SamlController.class);
    
    /**
     * Saves the provider.
     *
     * @param file the metadata file
     * @param name the provider name
     * @param url the metadata url
     * @param entityId the entityId
     * @param activeStatus the provider status
     * @param idpAttribute the idpAttribute
     * @param userCreation the userCreation
     * @param roleIds the role Ids
     * @return the SamlConfigResponse
     */
    @PostMapping("/save")
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public SamlConfigResponse uploadFile(@RequestParam(name = "file", required = false) MultipartFile file, 
            @RequestParam(name = "name", required = true) String name, 
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "entityId", required = true) String entityId,
            @RequestParam(name = "activeStatus", required = true) boolean activeStatus,
            @RequestParam(name = "idpAttribute", required = true) String idpAttribute,
            @RequestParam(name = "userCreation", required = true) boolean userCreation,
            @RequestParam(name = "roleIds", required = false) List<Long> roleIds) {
        SamlConfigResponse entity = idpService.storeFile(file, name, url, entityId, activeStatus, 
                userCreation, roleIds, idpAttribute);
        try {
            idpService.loadIdpMetadata(entity.getIdpId(), entity.getIdpProviderType().name());
        } catch (MetadataProviderException e) {
            e.printStackTrace();
        }
        return entity;
    }
    
    /**
     * Updates the provider.
     *
     * @param file the metadata file
     * @param idpUrl the metadata url
     * @param name the provider name
     * @param entityId the entityId
     * @param activeStatus the provider status
     * @param idpAttribute the idpAttribute
     * @param userCreation the userCreation
     * @param roleIds the role Ids
     * @return the SamlConfigResponse
     */
    @RequestMapping(value = "/update/{idp_id}", method = RequestMethod.PATCH)
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public SamlConfigResponse updateIdp(@PathVariable("idp_id") String idpId, 
            @RequestParam(name = "file", required = false) MultipartFile file, 
            @RequestParam(name = "name", required = true) String name, 
            @RequestParam(name = "url", required = false) String idpUrl,
            @RequestParam(name = "entityId", required = false) String entityId,
            @RequestParam(name = "activeStatus", required = true) boolean activeStatus,
            @RequestParam(name = "idpAttribute", required = true) String idpAttribute,
            @RequestParam(name = "userCreation", required = true) boolean userCreation,
            @RequestParam(name = "roleIds", required = false) List<Long> roleIds) {
        SamlConfigResponse res = idpService.updateIdp(idpId, file, name, idpUrl, entityId, activeStatus, 
                idpAttribute, userCreation, roleIds);
        if (res.isRequireUpdate()) {
            idpService.updateIdpMetadata(res.getIdpId(), res.getIdpProviderType().name());
        }
        return res;
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{idpId}", method = RequestMethod.GET)
    public SamlConfigResponse getById(@PathVariable("idpId") final String idpId) {
        SamlConfigResponse identityProviderResponse = idpService.getById(idpId);
        return identityProviderResponse;
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{idp_id}", method = RequestMethod.DELETE)
    public Message deleteByIdpId(@PathVariable("idp_id") final String id) {
        return idpService.deleteIdpByIdpId(id);
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/update/status/{idp_id}", method = RequestMethod.PUT)
    public SamlConfigResponse updateIdpStatus(@PathVariable("idp_id") final String id, @RequestParam boolean status) {
        return idpService.updateIdpStatus(id, status);
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/getAll", method = RequestMethod.GET)
    public List<SamlConfigResponse> getAll() {
        List<SamlConfigResponse> idpResponseList = idpService.getAll();
        return idpResponseList;
    }
    
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/getAll/active", method = RequestMethod.GET)
    public List<SamlConfigResponse> getAllActiveIdp() {
        List<SamlConfigResponse> idpResponseList = idpService.getAllActiveIdp();
        return idpResponseList;
    }
    
}
