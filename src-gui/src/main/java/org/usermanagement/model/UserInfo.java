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

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Class UserInfo.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "user_id", "name", "user_name", "roles", "is2FaEnabled", "status", "email", "role_id", "password",
        "status", "new_password", "code", "settings" })
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 5779373512137456449L;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("user_name")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("role")
    private String role;

    @JsonProperty("is2FaEnabled")
    private Boolean is2FaEnabled;

    @JsonProperty("roles")
    private Set<String> roles;

    @JsonProperty("permissions")
    private Set<String> permissions;

    @JsonProperty("email")
    private String email;

    @JsonProperty("role_id")
    private List<Long> roleIds;

    @JsonProperty("password")
    private String password;

    @JsonProperty("new_password")
    private String newPassword;

    @JsonProperty("status")
    private String status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("settings")
    private String data;

    /**
     * Gets the user id.
     *
     * @return the user id
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the user id.
     *
     * @param userId the new user id
     */
    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username the new username
     */
    public void setUsername(final String username) {
        this.username = username;
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
    public void setRole(final String role) {
        this.role = role;
    }

    /**
     * Gets the roles.
     *
     * @return the roles
     */
    public Set<String> getRoles() {
        if (roles == null) {
            roles = new HashSet<>();
        }
        return roles;
    }

    /**
     * Sets the roles.
     *
     * @param roles the new roles
     */
    public void setRoles(final Set<String> roles) {
        this.roles = roles;
    }

    /**
     * Gets the email.
     *
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email.
     *
     * @param email the new email
     */
    public void setEmail(final String email) {
        this.email = email;
    }

    /**
     * Gets the role ids.
     *
     * @return the role ids
     */
    public List<Long> getRoleIds() {
        return roleIds;
    }

    /**
     * Sets the role ids.
     *
     * @param roleIds the new role ids
     */
    public void setRoleIds(final List<Long> roleIds) {
        this.roleIds = roleIds;
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
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password the new password
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * Gets the permissions.
     *
     * @return the permissions
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Sets the permissions.
     *
     * @param permissions the new permissions
     */
    public void setPermissions(final Set<String> permissions) {
        this.permissions = permissions;
    }

    /**
     * Get the new password.
     * 
     * @return the new password.
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * Sets the new password.
     * 
     * @param newPassword the new password.
     */
    public void setNewPassword(final String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * Gets the checks if is 2 fa enabled.
     *
     * @return the checks if is 2 fa enabled.
     */
    public Boolean getIs2FaEnabled() {
        return is2FaEnabled;
    }

    /**
     * Sets the checks if is 2 fa enabled.
     *
     * @param is2FaEnabled the new checks if is 2 fa enabled
     */
    public void setIs2FaEnabled(final Boolean is2FaEnabled) {
        this.is2FaEnabled = is2FaEnabled;
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
     * Sets the code.
     *
     * @param code the new code
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Gets the data.
     *
     * @return the data
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the data.
     *
     * @param data the new data
     */
    public void setData(String data) {
        this.data = data;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "UserInfo [userId=" + userId + ", username=" + username + ", name=" + name + ", roles=" + roles
                + ", email=" + email + ", roleIds=" + roleIds + ", status=" + status + ", is2FaEnabled=" + is2FaEnabled
                + ", code=" + code + "]";
    }
}
