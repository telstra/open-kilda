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

package org.openkilda.saml.validator;

import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.repository.SamlRepository;

import org.apache.commons.io.FilenameUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.URL;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

@Component
public class SamlValidator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SamlValidator.class);
    
    @Autowired
    private SamlRepository samlRepository;
    
    @Autowired
    private MessageUtils messageUtil;

    /**
     * Validate create provider.
     *
     * @param file the metadata file
     * @param name the provider name
     * @param entityId the entityId
     * @param url the metadata url
     * @param userCreation the userCreation
     * @param roleIds the role ids
     */
    public void validateCreateProvider(MultipartFile file, String name, String entityId, String url, 
            boolean userCreation, List<Long> roleIds) {
        SamlConfigEntity samlConfigEntity = samlRepository.findByEntityIdOrNameEqualsIgnoreCase(entityId, name);
        if (samlConfigEntity != null) {
            throw new RequestValidationException(messageUtil.getAttributeUnique("Provider name or Entity Id"));
        }
        if (file == null && url == null) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("Metadata file or url"));
        }
        if (file != null) {
            if (!FilenameUtils.getExtension(file.getOriginalFilename()).equals("xml")) {
                throw new RequestValidationException(messageUtil.getAttributeMetadataInvalid("file"));
            }
        }
        String metadataEntityId = validateEntityId(file, url);
        if (!metadataEntityId.equals(entityId)) { 
            throw new RequestValidationException("Entity Id must be same as Metadata Entity Id");
        }
        if (userCreation) {
            if (roleIds.isEmpty() || roleIds == null) {
                throw new RequestValidationException(messageUtil.getAttributeNotNull("role"));
            }
        }
    }
    
    /**
     * Validate update provider.
     *
     * @param uuid the uuid
     * @param file the metadata file
     * @param name the provider name
     * @param entityId the entityId
     * @param url the metadata url
     * @param userCreation the userCreation
     * @param roleIds the role ids
     * @return the saml config entity
     */
    public SamlConfigEntity validateUpdateProvider(String uuid, MultipartFile file, String name, 
            String entityId, String url, boolean userCreation, List<Long> roleIds) {
        SamlConfigEntity samlConfigEntity = getEntityByUuid(uuid);
        SamlConfigEntity configEntity = samlRepository.findByUuidNotAndEntityIdOrUuidNotAndNameEqualsIgnoreCase(
                uuid, entityId, uuid, name);
        if (configEntity != null) {
            throw new RequestValidationException(messageUtil.getAttributeUnique("Provider name or Entity Id"));
        }
        if (file != null) {
            if (!FilenameUtils.getExtension(file.getOriginalFilename()).equals("xml")) {
                throw new RequestValidationException(messageUtil.getAttributeMetadataInvalid("file"));
            }
        }
        if (file == null && url == null) {
            if (!samlConfigEntity.getEntityId().equals(entityId)) {
                throw new RequestValidationException(messageUtil.getAttributeInvalid("Entity Id", entityId));
            }
        } else {
            String metadataEntityId = validateEntityId(file, url);
            if (!metadataEntityId.equals(entityId)) {
                throw new RequestValidationException("Entity Id must be same as Metadata Entity Id");
            } 
        }
        if (userCreation) {
            if (roleIds.isEmpty() || roleIds == null) {
                throw new RequestValidationException(messageUtil.getAttributeNotNull("role"));
            }
        }
        return samlConfigEntity;
    }
    
    /**
     * Validate entity id.
     *
     * @param file the metadata file
     * @param url the metadata url
     * @return the entity id
     */
    private String validateEntityId(MultipartFile file, String url) {
        String entityId = null;
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
            Document doc = null; 
            if (file != null) {
                doc = docBuilder.parse(file.getInputStream());
            } else if (url != null) {
                doc = docBuilder.parse(new URL(url).openStream());
            }
            doc.getDocumentElement().normalize();
            NodeList nodeList = doc.getElementsByTagName(doc.getDocumentElement().getNodeName());
            for (int temp = 0; temp < nodeList.getLength(); temp++) {
                Node node = nodeList.item(temp);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    entityId = element.getAttribute("entityID");
                }
            }
            return entityId;
        } catch (Exception e) {
            LOGGER.error("Error occurred while validating entity ID" + e);
            throw new RequestValidationException(messageUtil.getAttributeMetadataInvalid("url"));
        }
    }
    
    /**
     * Get saml config entity.
     *
     * @param uuid the uuid
     * @return the SamlConfigEntity
     */
    public SamlConfigEntity getEntityByUuid(String uuid) {
        SamlConfigEntity samlConfigEntity = samlRepository.findByUuid(uuid);
        if (samlConfigEntity == null) {
            throw new RequestValidationException(messageUtil.getAttributeNotFound("Provider"));
        }
        return samlConfigEntity;
    }
}
