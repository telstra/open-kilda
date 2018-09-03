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

package org.openkilda.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The Class Role.
 * 
 * @author sumitpal.singh
 */
@Entity
@Table(name = "role")
public class Role implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The user role id. */
    @Id
    @Column(name = "user_role_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long userRoleId;

    /** The role. */
    @Column(name = "user_role", nullable = false)
    private String role;

    /**
     * Gets the user role id.
     *
     * @return the user role id
     */
    public Long getUserRoleId() {
        return userRoleId;
    }

    /**
     * Sets the user role id.
     *
     * @param userRoleId the new user role id
     */
    public void setUserRoleId(Long userRoleId) {
        this.userRoleId = userRoleId;
    }

    /**
     * Gets the role.
     *
     * @return the role
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role.
     *
     * @param role the new role
     */
    public void setRole(String role) {
        this.role = role;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Role [userRoleId=" + userRoleId + ", role=" + role + "]";
    }

}
