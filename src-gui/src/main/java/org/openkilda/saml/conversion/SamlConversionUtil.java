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

package org.openkilda.saml.conversion;

import org.openkilda.constants.IConstants;
import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.model.SamlConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.exception.RequestValidationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialException;


public final class SamlConversionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SamlConversionUtil.class);
    
    private SamlConversionUtil() {

    }
    
    /**
     * To saml config.
     *
     * @param samlConfigEntity the saml config entity
     * @return the saml config
     */
    public static SamlConfig toSamlConfig(SamlConfigEntity samlConfigEntity) {
        SamlConfig samlConfig = new SamlConfig();
        samlConfig.setName(samlConfigEntity.getName());
        samlConfig.setUrl(samlConfigEntity.getUrl());
        samlConfig.setEntityId(samlConfigEntity.getEntityId());
        samlConfig.setUuid(samlConfigEntity.getUuid());
        samlConfig.setUserCreation(samlConfigEntity.isUserCreation());
        samlConfig.setStatus(samlConfigEntity.isStatus());
        samlConfig.setType(samlConfigEntity.getType());
        samlConfig.setAttribute(samlConfigEntity.getAttribute());
        Set<Long> roles = new HashSet<>();
        if (samlConfigEntity.getRoles() != null) {
            for (RoleEntity roleEntity : samlConfigEntity.getRoles()) {
                roles.add(roleEntity.getRoleId());
            }
            samlConfig.setRoles(roles);
        }
        return samlConfig;
    }
    
    /**
     * To saml config entity.
     *
     * @param file the metadata file
     * @param name the provider name
     * @param url the metadata url
     * @param entityId the entityId
     * @param status the provider status
     * @param attribute the attribute
     * @param userCreation the userCreation
     * @param roleEntities the role entities
     * @return the saml config entity
     */
    public static SamlConfigEntity toSamlConfigEntity(MultipartFile file, String name, String url, String entityId,
            boolean status, String attribute, boolean userCreation, Set<RoleEntity> roleEntities) {

        SamlConfigEntity samlConfigEntity = new SamlConfigEntity();
        Blob blob = null;
        try {
            if (file != null) {
                byte[] bytes = file.getBytes();
                try {
                    blob = new SerialBlob(bytes);
                } catch (SerialException e) {
                    LOGGER.error("Error occurred while saving saml provider" + e);
                } catch (SQLException e) {
                    LOGGER.error("Error occurred while saving saml provider" + e);
                }
                samlConfigEntity.setType(IConstants.ProviderType.FILE);
            } else if (url != null) {
                samlConfigEntity.setType(IConstants.ProviderType.URL);
            } 
            if (userCreation) {
                samlConfigEntity.setUserCreation(true);
                samlConfigEntity.setRoles(roleEntities);
            }
            samlConfigEntity.setMetadata(blob);
            samlConfigEntity.setEntityId(entityId);
            samlConfigEntity.setAttribute(attribute);
            samlConfigEntity.setUrl(url);
            samlConfigEntity.setName(name);
            samlConfigEntity.setStatus(status);
            samlConfigEntity.setUuid(UUID.randomUUID().toString());
            return samlConfigEntity;
        } catch (FileNotFoundException e) {
            LOGGER.error("Error occurred while saving saml provider" + e);
        } catch (IOException e) {
            LOGGER.error("Error occurred while saving saml provider" + e);
        }
        return samlConfigEntity;
    }

    /**
     * To update saml config entity.
     *
     * @param samlConfigEntity the saml config entity
     * @param roleEntities the role entities
     * @param file the metadata file
     * @param name the provider name
     * @param url the metadata url
     * @param entityId the entityId
     * @param status the provider status
     * @param userCreation the userCreation
     * @param attribute the attribute
     * @return the requireManagerUpdateStatus
     */
    public static boolean toUpdateSamlConfigEntity(SamlConfigEntity samlConfigEntity, Set<RoleEntity> roleEntities, 
            MultipartFile file, String name, String url, String entityId, boolean status, 
            boolean userCreation, String attribute) {
        Blob blob = null;
        boolean requireManagerUpdate = false;
        try {
            if (file != null) {
                byte[] bytes = file.getBytes();
                try {
                    blob = new SerialBlob(bytes);
                } catch (SerialException e) {
                    LOGGER.error("Error occurred while updating saml provider" + e);
                } catch (SQLException e) {
                    LOGGER.error("Error occurred while updating saml provider" + e);
                }
                samlConfigEntity.setMetadata(blob);
                samlConfigEntity.setType(IConstants.ProviderType.FILE);
                requireManagerUpdate = true;
                samlConfigEntity.setUrl(null);
            } else if (url != null) {
                samlConfigEntity.setUrl(url);
                samlConfigEntity.setType(IConstants.ProviderType.URL);
                requireManagerUpdate = true;
                samlConfigEntity.setMetadata(null);
            } 
            samlConfigEntity.setEntityId(entityId);
            if (userCreation) {
                samlConfigEntity.setUserCreation(true);
                if (!samlConfigEntity.getRoles().isEmpty()) {
                    samlConfigEntity.getRoles().clear();
                }
                samlConfigEntity.getRoles().addAll(roleEntities);
            } else {
                samlConfigEntity.setRoles(null);
            }
            samlConfigEntity.setName(name);
            samlConfigEntity.setUserCreation(userCreation);
            samlConfigEntity.setAttribute(attribute);
            samlConfigEntity.setStatus(status);
        } catch (RequestValidationException e) {
            throw new RequestValidationException(e.getMessage());
        } catch (FileNotFoundException e) {
            LOGGER.error("Error occurred while updating saml provider" + e);
        } catch (IOException e) {
            LOGGER.error("Error occurred while updating saml provider" + e);
        } 
        return requireManagerUpdate;
    }
}
