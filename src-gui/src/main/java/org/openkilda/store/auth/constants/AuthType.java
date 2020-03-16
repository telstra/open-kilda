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

package org.openkilda.store.auth.constants;

import org.openkilda.store.auth.dao.entity.AuthTypeEntity;

/**
 * The Enum AuthType.
 */
public enum AuthType {

    OAUTH_TWO("OAUTH_TWO");
    
    private String code;

    /** The auth type entity. */
    private AuthTypeEntity authTypeEntity;
    
    /**
     * Instantiates a new auth type.
     *
     * @param code the code
     */
    private AuthType(final String code) {
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
     * Gets the auth type entity.
     *
     * @return the auth type entity
     */
    public AuthTypeEntity getAuthTypeEntity() {
        return authTypeEntity;
    }

    /**
     * Sets the auth type entity.
     *
     * @param authTypeEntity the new auth type entity
     */
    public void setAuthTypeEntity(final AuthTypeEntity authTypeEntity) {
        if (this.authTypeEntity == null) {
            this.authTypeEntity = authTypeEntity;
        }
    }

    /**
     * Gets the auth type by code.
     *
     * @param code the code
     * @return the auth type by code
     */
    public static AuthType getAuthTypeByCode(final String code) {
        AuthType authType = null;
        for (AuthType authTypeObj : AuthType.values()) {
            if (authTypeObj.getCode().equalsIgnoreCase(code)) {
                authType = authTypeObj;
                break;
            }
        }
        return authType;
    }

    /**
     * Gets the auth type by name.
     *
     * @param name the name
     * @return the auth type by name
     */
    public static AuthType getAuthTypeByName(final String name) {
        AuthType authType = null;
        for (AuthType authTypeObj : AuthType.values()) {
            if (authTypeObj.getAuthTypeEntity().getAuthTypeName().equalsIgnoreCase(name)) {
                authType = authTypeObj;
                break;
            }
        }
        return authType;
    }

    /**
     * Gets the auth type by id.
     *
     * @param id the id
     * @return the auth type by id
     */
    public static AuthType getAuthTypeById(final Integer id) {
        AuthType authType = null;
        for (AuthType authTypeObj : AuthType.values()) {
            if (authTypeObj.getAuthTypeEntity().getAuthTypeId() == (id)) {
                authType = authTypeObj;
                break;
            }
        }
        return authType;
    }

}
