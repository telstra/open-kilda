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

package org.openkilda.saml.provider;

import java.util.Timer;

public class DbMetadataProvider {

    public DbMetadataProvider() {
        super();
    }
    
    private String metaDataEntityId;  // unique Id for DB lookups
          
    /**
     * Constructor.
     * @param entityId the entity Id of the metadata.  Use as key to identify a database row.
    */

    public DbMetadataProvider(String entityId) {
        super();
        setMetaDataEntityId(entityId);
    }

    /**
     * Constructor.
     * @param backgroundTaskTimer timer used to refresh metadata in the background
     * @param entityId the entity Id of the metadata.  Use as key to identify a database row.
    */

    public DbMetadataProvider(Timer backgroundTaskTimer, String entityId) {
        setMetaDataEntityId(entityId);
    }

    public String getMetaDataEntityId() { 
        return metaDataEntityId;
    }
    
    public void setMetaDataEntityId(String metaDataEntityId) {
        this.metaDataEntityId = metaDataEntityId;
    }

}
