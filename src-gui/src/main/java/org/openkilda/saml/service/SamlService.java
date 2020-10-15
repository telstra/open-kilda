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

package org.openkilda.saml.service;

import org.openkilda.saml.conversion.SamlConversionUtil;
import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.manager.SamlMetadataManager;
import org.openkilda.saml.model.SamlConfig;
import org.openkilda.saml.repository.SamlRepository;
import org.openkilda.saml.validator.SamlValidator;

import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.model.Message;
import org.usermanagement.service.RoleService;

import java.sql.Blob;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
public class SamlService {

    @Autowired
    private SamlRepository samlRepository;
      
    @Autowired
    private RoleService roleService;
    
    @Autowired
    private SamlValidator samlValidator;
    
    @Autowired
    private SamlMetadataManager metadataManager;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SamlService.class);

    /**
     * Creates the provider.
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
    
    public SamlConfig create(MultipartFile file, String name, String url, String entityId,
            boolean status, boolean userCreation, List<Long> roleIds, String attribute) {
        samlValidator.validateCreateProvider(file, name, entityId, url, userCreation, roleIds);
        Set<RoleEntity> roleEntities = roleService.getRolesById(roleIds);
        SamlConfigEntity samlConfigEntity = SamlConversionUtil.toSamlConfigEntity(file, name, url, 
                entityId, status, attribute, userCreation, roleEntities);
        samlRepository.save(samlConfigEntity);
        try {
            metadataManager.loadProviderMetadata(samlConfigEntity.getUuid(), samlConfigEntity.getType().name());
        } catch (MetadataProviderException e) {
            LOGGER.error("Error occurred while loading provider" + e);
        }
        return SamlConversionUtil.toSamlConfig(samlConfigEntity);
    }

     
    /**
     * Updates the provider.
     *
     * @param uuid the uuid
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
    public SamlConfig update(String uuid, MultipartFile file, String name, String url,
            String entityId, boolean status, String attribute, boolean userCreation, List<Long> roleIds) {
        SamlConfigEntity samlConfigEntity = samlValidator.validateUpdateProvider(uuid, file, name, entityId, url, 
                userCreation, roleIds);

        Set<RoleEntity> roleEntities = roleService.getRolesById(roleIds);
        boolean requireManagerUpdate = SamlConversionUtil.toUpdateSamlConfigEntity(samlConfigEntity, roleEntities,
                file, name, url, entityId, status, userCreation, attribute);
        samlRepository.save(samlConfigEntity);
        if (requireManagerUpdate) {
            metadataManager.updateProviderToMetadataManager(samlConfigEntity.getUuid(), 
                    samlConfigEntity.getType().name());
        }
        return SamlConversionUtil.toSamlConfig(samlConfigEntity);
    }

    
    /**
     * Gets the provider detail.
     * @param uuid the uuid of provider.
     * @return the SamlConfig
     */
    @Transactional
    public SamlConfig getById(String uuid) {
        SamlConfigEntity samlConfigEntity = samlValidator.getEntityByUuid(uuid);
        SamlConfig samlConfig = SamlConversionUtil.toSamlConfig(samlConfigEntity);
        Blob blob = samlConfigEntity.getMetadata();
        byte[] bdata;
        if (blob != null) {
            try {
                bdata = blob.getBytes(1, (int) blob.length());
                String metadata = new String(bdata);
                samlConfig.setMetadata(metadata);
            } catch (Exception e) {
                LOGGER.error("Error occurred while getting provider detail" + e);
            }
        }
        return samlConfig;
    }
    
    /**
     * Delete provider.
     *
     * @param uuid the uuid
     * @return delete message
     */
    public Message deleteByUuid(String uuid) {
        SamlConfigEntity samlConfigEntity = samlValidator.getEntityByUuid(uuid);
        samlRepository.delete(samlConfigEntity);
        metadataManager.deleteProviderFromMetadataManager(samlConfigEntity);
        return new Message("Provider deleted successfully");
    }
    
    /**
     * Gets all the providers.
     *
     * @return the providers 
     */
    public List<SamlConfig> getAll() {
        List<SamlConfigEntity> samlConfigEntityList = samlRepository.findAll();
        List<SamlConfig> samlConfigList = new ArrayList<>();
        for (SamlConfigEntity samlConfigEntity : samlConfigEntityList) {
            SamlConfig samlConfig = SamlConversionUtil.toSamlConfig(samlConfigEntity);
            samlConfigList.add(samlConfig);
        }
        return samlConfigList;
    }
    
    /**
     * Gets all the active providers.
     *
     * @return the active providers
     */
    public List<SamlConfig> getAllActiveIdp() {
        List<SamlConfigEntity> samlConfigEntityList = samlRepository.findAllByStatus(true);
        List<SamlConfig> samlConfigList = new ArrayList<>();

        for (SamlConfigEntity samlConfigEntity : samlConfigEntityList) {
            SamlConfig samlConfig = SamlConversionUtil.toSamlConfig(samlConfigEntity);
            samlConfigList.add(samlConfig);
        }
        return samlConfigList;
    }
    
    public SamlConfig getConfigByEntityId(String entityId) {
        SamlConfigEntity samlConfigEntity = samlRepository.findByEntityId(entityId);
        return SamlConversionUtil.toSamlConfig(samlConfigEntity);
    }
}
