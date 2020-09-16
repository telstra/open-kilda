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

package org.openkilda.saml.service;

import org.openkilda.constants.IConstants;
import org.openkilda.saml.entity.SamlConfig;
import org.openkilda.saml.model.SamlConfigResponse;
import org.openkilda.saml.provider.DbMetadataProvider;
import org.openkilda.saml.provider.UrlMetadataProvider;
import org.openkilda.saml.repository.SamlRepository;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.io.FilenameUtils;

import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.saml.metadata.CachingMetadataManager;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Message;
import org.usermanagement.service.RoleService;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class SamlService {

    @Autowired
    private SamlRepository idpRepository;

    @Autowired
    private CachingMetadataManager  metadataManager;
      
    @Autowired
    private RoleService roleService;

    /**
     * Saves the provider.
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
    public SamlConfigResponse storeFile(MultipartFile file, String name, String idpUrl, String entityId,
            boolean activeStatus, boolean userCreation, List<Long> roleIds, String idpAttribute) {
        SamlConfig entity;
        Blob blob = null;
        entity = idpRepository.findByEntityIdOrIdpNameEqualsIgnoreCase(entityId, name);
        if (entity != null) {
            throw new RequestValidationException("Provider name or Entity Id must be unique");
        }
        entity = new SamlConfig();
        try {
            if (file != null) {
                if (!FilenameUtils.getExtension(file.getOriginalFilename()).equals("xml")) {
                    throw new RequestValidationException("Invalid metadata file");
                }
                byte[] bytes = file.getBytes();
                try {
                    blob = new SerialBlob(bytes);
                } catch (SerialException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                entity.setIdpProviderType(IConstants.IdpProviderType.FILE);
            } else if (idpUrl != null) {
                entity.setIdpProviderType(IConstants.IdpProviderType.URL);
            } else {
                throw new RequestValidationException("Please specify metadata file or url");
            }
            String metadataEntityId = getEntityId(file, idpUrl);
            if (!metadataEntityId.equals(entityId)) { 
                throw new RequestValidationException("Entity Id must be same as Metadata Entity Id");
            }
            if (userCreation) {
                entity.setAllowUserCreation(true);
                if (roleIds.isEmpty() || roleIds == null) {
                    throw new RequestValidationException("Roles cannot be null");
                }
                Set<RoleEntity> roleEntities = roleService.getRolesById(roleIds);
                entity.setRoles(roleEntities);
            }
            entity.setIdpMetadata(blob);
            entity.setEntityId(entityId);
            entity.setIdpAttribute(idpAttribute);
            entity.setIdpUrl(idpUrl);
            entity.setIdpName(name);
            entity.setActiveStatus(activeStatus);
            entity.setIdpId(UUID.randomUUID().toString());
            idpRepository.save(entity);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return toSamlConfigResponse(entity);
    }
    
    /**
     * To samlconfig response.
     *
     * @param samlEntity the saml entity
     * @return the samlconfig response
     */
    public static SamlConfigResponse toSamlConfigResponse(final SamlConfig samlEntity) {
        SamlConfigResponse samlRes = new SamlConfigResponse();
        samlRes.setIdpName(samlEntity.getIdpName());
        samlRes.setIdpUrl(samlEntity.getIdpUrl());
        samlRes.setEntityId(samlEntity.getEntityId());
        samlRes.setIdpId(samlEntity.getIdpId());
        samlRes.setAllowUserCreation(samlEntity.isAllowUserCreation());
        samlRes.setActiveStatus(samlEntity.isActiveStatus());
        samlRes.setIdpProviderType(samlEntity.getIdpProviderType());
        samlRes.setSamlAttribute(samlEntity.getIdpAttribute());
        Set<Long> roles = new HashSet<>();
        if (samlEntity.getRoles() != null) {
            for (RoleEntity roleEntity : samlEntity.getRoles()) {
                roles.add(roleEntity.getRoleId());
            }
            samlRes.setRoles(roles);
        }
        return samlRes;
    }
    
    /**
     * To update samlconfig response.
     *
     * @param samlEntity the saml entity
     * @return the samlconfig response
     */
    public static SamlConfigResponse toUpdateSamlConfigResponse(final SamlConfig samlEntity, final boolean update) {
        SamlConfigResponse samlRes = new SamlConfigResponse();
        samlRes.setIdpName(samlEntity.getIdpName());
        samlRes.setIdpUrl(samlEntity.getIdpUrl());
        samlRes.setEntityId(samlEntity.getEntityId());
        samlRes.setIdpId(samlEntity.getIdpId());
        samlRes.setAllowUserCreation(samlEntity.isAllowUserCreation());
        samlRes.setActiveStatus(samlEntity.isActiveStatus());
        samlRes.setIdpProviderType(samlEntity.getIdpProviderType());
        samlRes.setSamlAttribute(samlEntity.getIdpAttribute());
        samlRes.setRequireUpdate(update);
        Set<Long> roles = new HashSet<>();
        if (samlEntity.getRoles() != null) {
            for (RoleEntity roleEntity : samlEntity.getRoles()) {
                roles.add(roleEntity.getRoleId());
            }
            samlRes.setRoles(roles);
        }
        return samlRes;
    }

    private String getEntityId(MultipartFile file, String idpUrl) {
        String entityId = null;
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
            Document doc = null; 
            if (file != null) {
                doc = docBuilder.parse(file.getInputStream());
            } else if (idpUrl != null) {
                doc = docBuilder.parse(new URL(idpUrl).openStream());
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
            e.printStackTrace();
        }
        return entityId;
    }
     
    /**
     * Updates the provider.
     *
     * @param idpId the idp Id
     * @param file the metadata file
     * @param name the provider name
     * @param idpUrl the metadata url
     * @param entityId the entityId
     * @param activeStatus the provider status
     * @param idpAttribute the idpAttribute
     * @param userCreation the userCreation
     * @param roleIds the role Ids
     * @return the SamlConfigResponse
     */
    public SamlConfigResponse updateIdp(String idpId, MultipartFile file, String name, String idpUrl,
            String entityId, boolean activeStatus, String idpAttribute, boolean userCreation, List<Long> roleIds) {
        Blob blob = null;
        boolean requireManagerUpdate = false;
        SamlConfig entity = idpRepository.findByIdpId(idpId);
        if (entity == null) {
            throw new RequestValidationException("invalid id");
        }
        SamlConfig entity1 = idpRepository.findByIdpIdNotAndEntityIdOrIdpIdNotAndIdpNameEqualsIgnoreCase(
                idpId, entityId, idpId, name);
        if (entity1 == null) {
            try {
                if (file != null) {
                    if (!FilenameUtils.getExtension(file.getOriginalFilename()).equals("xml")) {
                        throw new RequestValidationException("Invalid metadata file");
                    }
                    byte[] bytes = file.getBytes();
                    try {
                        blob = new SerialBlob(bytes);
                    } catch (SerialException e) {
                        e.printStackTrace();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    entity.setIdpMetadata(blob);
                    entity.setIdpProviderType(IConstants.IdpProviderType.FILE);
                    requireManagerUpdate = true;
                    entity.setIdpUrl(null);
                } else if (idpUrl != null) {
                    entity.setIdpUrl(idpUrl);
                    entity.setIdpProviderType(IConstants.IdpProviderType.URL);
                    requireManagerUpdate = true;
                    entity.setIdpMetadata(null);
                } 
                if (file == null && idpUrl == null) {
                    if (!entity.getEntityId().equals(entityId)) {
                        throw new RequestValidationException("Entity id cannot be updated");
                    }
                } else {
                    String metadataEntityId = getEntityId(file, idpUrl);
                    if (!metadataEntityId.equals(entityId)) {
                        throw new RequestValidationException("Entity Id must be same as Metadata Entity Id");
                    } else {
                        entity.setEntityId(entityId);
                    }
                }
                if (userCreation) {
                    if (roleIds.isEmpty() || roleIds == null) {
                        throw new RequestValidationException("Roles cannot be null");
                    }
                    entity.setAllowUserCreation(true);
                    if (!entity.getRoles().isEmpty()) {
                        entity.getRoles().clear();
                    }
                    Set<RoleEntity> roleEntities = roleService.getRolesById(roleIds);
                    entity.getRoles().addAll(roleEntities);
                } else {
                    entity.setRoles(null);
                }
                entity.setIdpName(name);
                entity.setAllowUserCreation(userCreation);
                entity.setIdpAttribute(idpAttribute);
                entity.setActiveStatus(activeStatus);
                idpRepository.save(entity);
            } catch (RequestValidationException e) {
                throw new RequestValidationException(e.getMessage());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } 
        } else {
            throw new RequestValidationException("Provider name or Entity Id must be unique");
        }
        return toUpdateSamlConfigResponse(entity, requireManagerUpdate);
    }

    
    /**
     * Gets the provider detail.
     * @param id the id of provider requested.
     * @return the provider detail
     */
    @Transactional
    public SamlConfigResponse getById(String id) {
        SamlConfig entity = idpRepository.findByIdpId(id);
        if (entity == null) {
            throw new RequestValidationException("Metadata not found");
        }
        SamlConfigResponse idpRes = new SamlConfigResponse();
        if (entity != null) {
            idpRes.setIdpName(entity.getIdpName());
            idpRes.setIdpUrl(entity.getIdpUrl());
            idpRes.setEntityId(entity.getEntityId());
            idpRes.setIdpId(entity.getIdpId());
            idpRes.setIdpProviderType(entity.getIdpProviderType());
            idpRes.setAllowUserCreation(entity.isAllowUserCreation());
            idpRes.setActiveStatus(entity.isActiveStatus());
            idpRes.setSamlAttribute(entity.getIdpAttribute());
            Set<Long> roles = new HashSet<>();
            if (entity.getRoles() != null) {
                for (RoleEntity roleEntity : entity.getRoles()) {
                    roles.add(roleEntity.getRoleId());
                }
                idpRes.setRoles(roles);
            }
            Blob blob = entity.getIdpMetadata();
            byte[] bdata;
            if (blob != null) {
                try {
                    bdata = blob.getBytes(1, (int) blob.length());
                    String metadata = new String(bdata);
                    idpRes.setIdpMetadata(metadata);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return idpRes;
    }
    
    /**
     * loads the provider.
     *
     * @param idpId the id of provider.
     * @param type the type of provider.
     */
    public void loadIdpMetadata(String idpId, String type) throws MetadataProviderException {
        if (type.equals("URL")) {
            UrlMetadataProvider urlProvider = new UrlMetadataProvider(new Timer(true), new HttpClient(), idpId);
            urlProvider.setParserPool(ParserPoolHolder.getPool());
            addProviderToMetadataManager(urlProvider);
        } else if (type.equals("FILE")) {
            DbMetadataProvider dbProvider = new DbMetadataProvider(idpId);
            dbProvider.setParserPool(ParserPoolHolder.getPool());
            addProviderToMetadataManager(dbProvider);
        }
    }
    
    /**
     * adds the provider to metadata manager.
     *
     * @param metadataProvider the metadataProvider.
     */
    private void addProviderToMetadataManager(MetadataProvider metadataProvider) {
        ExtendedMetadata extMeta;
        List<MetadataProvider> providers = metadataManager.getProviders();
        extMeta = new ExtendedMetadata();
        extMeta.setIdpDiscoveryEnabled(false);
        extMeta.setSignMetadata(false);
        ExtendedMetadataDelegate delegate;
        delegate = new ExtendedMetadataDelegate(metadataProvider, extMeta);
        delegate.setMetadataTrustCheck(false);
        delegate.setMetadataRequireSignature(false);
        try {
            delegate.initialize();
            providers.add(delegate);
            metadataManager.setProviders(providers);
            metadataManager.refreshMetadata();
        } catch (MetadataProviderException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Delete provider.
     *
     * @param id the id
     * @return delete message
     */
    public Message deleteIdpByIdpId(String id) {
        SamlConfig entity = idpRepository.findByIdpId(id);
        if (entity == null) {
            throw new RequestValidationException("Idp id is not valid");
        }
        idpRepository.delete(entity);
        List<MetadataProvider> providers = metadataManager.getProviders();
        for (final MetadataProvider provider : providers) {
            try {
                String entityId = ((EntityDescriptor) provider.getMetadata()).getEntityID();
                if (entityId.equals(entity.getEntityId())) {
                    metadataManager.removeMetadataProvider(provider);
                    break;
                }
            } catch (MetadataProviderException e) {
                e.printStackTrace();
            }
        }
        return new Message("idp provider deleted successfully");
    }
    
    /**
     * Gets all the providers.
     *
     * @return the providers 
     */
    public List<SamlConfigResponse> getAll() {
        java.util.List<SamlConfig> idpList = idpRepository.findAll();
        List<SamlConfigResponse> idpResponseList = new ArrayList<>();
        for (SamlConfig providerEntity : idpList) {
            SamlConfigResponse res = toSamlConfigResponse(providerEntity);
            idpResponseList.add(res);
        }
        return idpResponseList;
    }
    
    /**
     * Gets all the active providers.
     *
     * @return the active providers
     */
    public List<SamlConfigResponse> getAllActiveIdp() {
        java.util.List<SamlConfig> idpList = idpRepository.findAllByActiveStatus(true);
        List<SamlConfigResponse> idpResponseList = new ArrayList<>();

        for (SamlConfig providerEntity : idpList) {
            SamlConfigResponse res = toSamlConfigResponse(providerEntity);
            idpResponseList.add(res);
        }
        return idpResponseList;
    }
    
    /**
     * Updates the provider status.
     * @param id the provider id
     * @param status the provider status
     * @return the samlconfig response
     */
    public SamlConfigResponse updateIdpStatus(String id, boolean status) {
        SamlConfig entity = idpRepository.findByIdpId(id);
        if (entity == null) {
            throw new RequestValidationException("Idp id is not valid");
        }
        entity.setActiveStatus(status);
        idpRepository.save(entity);
        return toSamlConfigResponse(entity);
    }
    
    /**
     * Updates the provider in metadata manager.
     * @param idpId the provider id
     * @param type the provider type
     */
    public void updateIdpMetadata(String idpId, String type) {
        List<ExtendedMetadataDelegate> provider1 = metadataManager.getAvailableProviders();
        String metadataEntityId = null;
        for (final ExtendedMetadataDelegate provider : provider1) {
            MetadataProvider ap = provider.getDelegate();
            if (ap instanceof DbMetadataProvider) {
                DbMetadataProvider dbprovider = (DbMetadataProvider) provider.getDelegate();
                metadataEntityId = dbprovider.getMetaDataEntityId();
            } else if (ap instanceof UrlMetadataProvider) {
                UrlMetadataProvider urlprovider = (UrlMetadataProvider) provider.getDelegate();
                metadataEntityId = urlprovider.getMetaDataEntityId();
            }
            if (idpId.equals(metadataEntityId)) {
                metadataManager.removeMetadataProvider(provider);
                break;
            }
        }
        try {
            loadIdpMetadata(idpId, type);
        } catch (MetadataProviderException e) {
            e.printStackTrace();
        }
    }
}
