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

package org.openkilda.saml.manager;

import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.provider.DbMetadataProvider;
import org.openkilda.saml.provider.UrlMetadataProvider;

import org.apache.commons.httpclient.HttpClient;

import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.saml.metadata.CachingMetadataManager;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Timer;

@Component
public class SamlMetadataManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SamlMetadataManager.class);

    @Autowired
    private CachingMetadataManager metadataManager;
    
    /**
     * loads the provider.
     *
     * @param uuid the id of provider.
     * @param type the type of provider.
     */
    public void loadProviderMetadata(String uuid, String type) throws MetadataProviderException {
        if (type.equals("URL")) {
            UrlMetadataProvider urlProvider = new UrlMetadataProvider(new Timer(true), new HttpClient(), uuid);
            urlProvider.setParserPool(ParserPoolHolder.getPool());
            addProviderToMetadataManager(urlProvider);
        } else if (type.equals("FILE")) {
            DbMetadataProvider dbProvider = new DbMetadataProvider(uuid);
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
        List<MetadataProvider> providers = metadataManager.getProviders();
        ExtendedMetadata extMeta = new ExtendedMetadata();
        extMeta.setIdpDiscoveryEnabled(false);
        extMeta.setSignMetadata(false);
        ExtendedMetadataDelegate delegate = new ExtendedMetadataDelegate(metadataProvider, extMeta);
        delegate.setMetadataTrustCheck(false);
        delegate.setMetadataRequireSignature(false);
        try {
            delegate.initialize();
            providers.add(delegate);
            metadataManager.setProviders(providers);
            metadataManager.refreshMetadata();
        } catch (MetadataProviderException e) {
            LOGGER.error("Error occurred while adding provider to metadata manager" + e);
        }
    }
    
    /**
     * Updates the provider in metadata manager.
     * @param uuid the provider id
     * @param type the provider type
     */
    public void updateProviderToMetadataManager(String uuid, String type) {
        List<ExtendedMetadataDelegate> providers = metadataManager.getAvailableProviders();
        String metadataEntityId = null;
        for (final ExtendedMetadataDelegate provider : providers) {
            MetadataProvider metadataProvider = provider.getDelegate();
            if (metadataProvider instanceof DbMetadataProvider) {
                DbMetadataProvider dbprovider = (DbMetadataProvider) provider.getDelegate();
                metadataEntityId = dbprovider.getMetaDataEntityId();
            } else if (metadataProvider instanceof UrlMetadataProvider) {
                UrlMetadataProvider urlprovider = (UrlMetadataProvider) provider.getDelegate();
                metadataEntityId = urlprovider.getMetaDataEntityId();
            }
            if (uuid.equals(metadataEntityId)) {
                metadataManager.removeMetadataProvider(provider);
                break;
            }
        }
        try {
            loadProviderMetadata(uuid, type);
        } catch (MetadataProviderException e) {
            LOGGER.error("Error occurred while updating provider in metadata manager" + e);
        }
    }

    /**
     * Deletes the provider from metadata manager.
     * @param samlConfigEntity the saml config entity
     */
    public void deleteProviderFromMetadataManager(SamlConfigEntity samlConfigEntity) {
        List<MetadataProvider> providers = metadataManager.getProviders();
        for (final MetadataProvider provider : providers) {
            try {
                String entityId = ((EntityDescriptor) provider.getMetadata()).getEntityID();
                if (entityId.equals(samlConfigEntity.getEntityId())) {
                    metadataManager.removeMetadataProvider(provider);
                    break;
                }
            } catch (MetadataProviderException e) {
                LOGGER.error("Error occurred while deleting provider from metadata manager" + e);
            }
        }
    }
}
