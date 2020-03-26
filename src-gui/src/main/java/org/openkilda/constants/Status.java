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

package org.openkilda.constants;

import org.usermanagement.dao.entity.StatusEntity;

public enum Status {

    /** The active. */
    ACTIVE("ACT"),
    /** The inactive. */
    INACTIVE("INA");
    /** The expired. */

    private String code;


    /** The status entity. */
    private StatusEntity statusEntity;

    /**
     * Instantiates a new status.
     *
     * @param code the code
     */
    private Status(final String code) {
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
     * Gets the status entity.
     *
     * @return the status entity
     */
    public StatusEntity getStatusEntity() {
        return statusEntity;
    }

    /**
     * Set status entity on loading of Tomcat server.
     *
     * @param statusEntity status entity.
     */
    public void setStatusEntity(final StatusEntity statusEntity) {
        if (this.statusEntity == null) {
            this.statusEntity = statusEntity;
        }
    }

    /**
     * Returns status by code.
     *
     * @param code the code
     * @return the status by code
     */
    public static Status getStatusByCode(final String code) {
        Status status = null;
        for (Status status2 : Status.values()) {
            if (status2.getCode().equalsIgnoreCase(code)) {
                status = status2;
                break;
            }
        }
        return status;
    }

    /**
     * Returns status by name.
     *
     * @param name the name
     * @return the status by name
     */
    public static Status getStatusByName(final String name) {
        Status status = null;
        for (Status status2 : Status.values()) {
            if (status2.getStatusEntity().getStatus().equalsIgnoreCase(name)) {
                status = status2;
                break;
            }
        }
        return status;
    }

    /**
     * Returns status by id.
     *
     * @param id the id
     * @return the status by id
     */
    public static Status getStatusById(final Integer id) {
        Status status = null;
        for (Status status2 : Status.values()) {
            if (status2.getStatusEntity().getStatusId() == (id)) {
                status = status2;
                break;
            }
        }
        return status;
    }

}
