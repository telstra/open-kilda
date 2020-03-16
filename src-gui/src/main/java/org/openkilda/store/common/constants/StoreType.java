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

package org.openkilda.store.common.constants;

import org.openkilda.store.common.dao.entity.StoreTypeEntity;

/**
 * The Enum StoreType.
 */

public enum StoreType {

    LINK_STORE("LINK_STORE"),
    SWITCH_STORE("SWITCH_STORE");
    
    private String code;

    private StoreTypeEntity storeTypeEntity;
    
    /**
     * Instantiates a new store type.
     *
     * @param code the code
     */
    private StoreType(final String code) {
        this.code = code;
    }

    /**
     * Gets the code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the store type entity.
     *
     * @return the store type entity
     */
    public StoreTypeEntity getStoreTypeEntity() {
        return storeTypeEntity;
    }

    /**
     * Sets the store type entity.
     *
     * @param storeTypeEntity the new store type entity
     */
    public void setStoreTypeEntity(final StoreTypeEntity storeTypeEntity) {
        if (this.storeTypeEntity == null) {
            this.storeTypeEntity = storeTypeEntity;
        }
    }

    /**
     * Gets the store type by code.
     *
     * @param code the code
     * @return the store type by code
     */
    public static StoreType getStoreTypeByCode(final String code) {
        StoreType storeType = null;
        for (StoreType storeTypeObj : StoreType.values()) {
            if (storeTypeObj.getCode().equalsIgnoreCase(code)) {
                storeType = storeTypeObj;
                break;
            }
        }
        return storeType;
    }

    /**
     * Gets the store type by name.
     *
     * @param name the name
     * @return the store type by name
     */
    public static StoreType getStoreTypeByName(final String name) {
        StoreType storeType = null;
        for (StoreType storeTypeObj : StoreType.values()) {
            if (storeTypeObj.getStoreTypeEntity().getStoreTypeName().equalsIgnoreCase(name)) {
                storeType = storeTypeObj;
                break;
            }
        }
        return storeType;
    }

    /**
     * Gets the configuration type by id.
     *
     * @param id the id
     * @return the configuration type by id
     */
    public static StoreType getConfigurationTypeById(final Integer id) {
        StoreType storeType = null;
        for (StoreType storeTypeObj : StoreType.values()) {
            if (storeTypeObj.getStoreTypeEntity().getStoreTypeId() == (id)) {
                storeType = storeTypeObj;
                break;
            }
        }
        return storeType;
    }

}
