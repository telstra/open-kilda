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

package org.openkilda.saml.provider;

import org.openkilda.saml.model.SamlConfig;
import org.openkilda.saml.service.SamlService;
import org.openkilda.security.ApplicationContextProvider;

import org.apache.commons.httpclient.HttpClient;
import org.opensaml.saml2.metadata.provider.HTTPMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;

import java.util.Timer;

public class UrlMetadataProvider extends HTTPMetadataProvider {

    public UrlMetadataProvider(String metadataUrl, int requestTimeout) throws MetadataProviderException {
        super(metadataUrl, requestTimeout);
    }

    private String metaDataEntityId;

    public UrlMetadataProvider() throws MetadataProviderException {
        super(new Timer(true), new HttpClient(), "");
    }

    /**
     * Constructor.
     * @param backgroundTaskTimer timer used to refresh metadata in the background
     * @param entityId the entity Id of the metadata.  Use as key to identify a database row.
    */
    public UrlMetadataProvider(Timer backgroundTaskTimer, HttpClient client, String entityId)
            throws MetadataProviderException {
        super(backgroundTaskTimer, client, entityId);
        setMetaDataEntityId(entityId);
    }

    public String getMetaDataEntityId() { 
        return metaDataEntityId;  
    }
    
    public void setMetaDataEntityId(String metaDataEntityId) {
        this.metaDataEntityId = metaDataEntityId; 
    }

    @Override
    protected String getMetadataIdentifier() { 
        return getMetaDataEntityId();
    }

    @Override
    public String getMetadataURI() { 
        SamlService samlService = ApplicationContextProvider.getContext().getBean(SamlService.class);
        SamlConfig samlConfig = samlService.getById(getMetaDataEntityId());
        return samlConfig.getUrl();
    } 
}
