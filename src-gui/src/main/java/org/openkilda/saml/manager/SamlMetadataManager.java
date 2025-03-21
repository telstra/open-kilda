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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SamlMetadataManager {

    /**
     * loads the provider.
     *
     * @param uuid the id of provider.
     * @param type the type of provider.
     */
    public void loadProviderMetadata(String uuid, String type) {
        throw new RuntimeException("Not supported");
    }

    /**
     * adds the provider to metadata manager.
     *
     * @param metadataProvider the metadataProvider.
     */
    private void addProviderToMetadataManager(String metadataProvider) {
    }

    /**
     * Updates the provider in metadata manager.
     * @param uuid the provider id
     * @param type the provider type
     */
    public void updateProviderToMetadataManager(String uuid, String type) {
    }

    /**
     * Deletes the provider from metadata manager.
     * @param samlConfigEntity the saml config entity
     */
    public void deleteProviderFromMetadataManager(String samlConfigEntity) {
    }
}
