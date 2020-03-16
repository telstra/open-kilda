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

package org.openkilda.store.common.dao.persistence;

import org.openkilda.config.DatabaseConfigurator;
import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.auth.dao.repository.AuthTypeRepository;
import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.dao.repository.StoreTypeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Class StoreConfigurator.
 */

@Repository("storeConfigurator")
public class StoreConfigurator {

    /** The store type repository. */
    private StoreTypeRepository storeTypeRepository;
    
    /** The auth type repository. */
    private AuthTypeRepository authTypeRepository;

    /**
     * Instantiates a new configurator.
     *
     * @param databaseConfigurator the database configurator
     * @param storeTypeRepository the store type repository
     * @param authTypeRepository the auth type repository
     */
    public StoreConfigurator(@Autowired final DatabaseConfigurator databaseConfigurator,
            @Autowired final StoreTypeRepository storeTypeRepository,
            @Autowired final AuthTypeRepository authTypeRepository) {
        this.storeTypeRepository = storeTypeRepository;
        this.authTypeRepository = authTypeRepository;
        init();
    }

    /**
     * Inits the.
     */
    public void init() {
        loadConfigurationType();
        loadAuthenticationType();
    }

    /**
     * Load configuration type.
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void loadConfigurationType() {
        storeTypeRepository.findAll().stream().forEach((entity) -> {
            for (StoreType storeype : StoreType.values()) {
                if (storeype.getCode().equalsIgnoreCase(entity.getStoreTypeCode())) {
                    storeype.setStoreTypeEntity(entity);
                }
            }
        });
    }
    
    /**
     * Load authentication type.
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void loadAuthenticationType() {
        authTypeRepository.findAll().stream().forEach((entity) -> {
            for (AuthType authType : AuthType.values()) {
                if (authType.getCode().equalsIgnoreCase(entity.getAuthTypeCode())) {
                    authType.setAuthTypeEntity(entity);
                }
            }
        });
    }

}
