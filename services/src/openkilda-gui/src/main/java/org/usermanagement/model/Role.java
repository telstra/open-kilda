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

package org.usermanagement.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * The Class Role.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"role_id", "name", "description", "status", "permission_id", "permissions", "users"})
public class Role {


    @JsonProperty("role_id")
    private Long roleId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status;

    /** The permission id. */
    @JsonProperty("permission_id")
    private List<Long> permissionId;

    @JsonProperty("permissions")
    private List<Permission> permissions;

    @JsonProperty("users")
    private List<UserInfo> userInfo;

    /**
     * Gets the role id.
     *
     * @return the role id
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * Sets the role id.
     *
     * @param roleId the new role id
     */
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    /**
     * Gets the permission id.
     *
     * @return the permission id
     */
    public List<Long> getPermissionId() {
        return permissionId;
    }

    /**
     * Sets the permission id.
     *
     * @param permissionId the new permission id
     */
    public void setPermissionId(List<Long> permissionId) {
        this.permissionId = permissionId;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the permissions.
     *
     * @return the permissions
     */
    public List<Permission> getPermissions() {
        return permissions;
    }

    /**
     * Sets the permissions.
     *
     * @param permissions the new permissions
     */
    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }



    public List<UserInfo> getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(List<UserInfo> userInfo) {
        this.userInfo = userInfo;
    }

    @Override
    public String toString() {
        return "Role [roleId=" + roleId + ", name=" + name + ", description=" + description + ", status=" + status
                + ", permissionId=" + permissionId + ", permissions=" + permissions + ", userInfo=" + userInfo + "]";
    }



}
