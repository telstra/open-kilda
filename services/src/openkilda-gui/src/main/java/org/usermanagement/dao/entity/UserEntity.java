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

package org.usermanagement.dao.entity;

import org.openkilda.entity.BaseEntity;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * The Class UserEntity.
 */

@Entity
@Table(name = "KILDA_USER")
public class UserEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "USER_ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long userId;

    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Column(name = "PASSWORD", nullable = false)
    private String password;

    @Column(name = "NAME")
    private String name;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "LOGIN_TIME")
    private Date loginTime;

    @Column(name = "LOGOUT_TIME")
    private Date logoutTime;

    @Column(name = "ACTIVE_FLAG")
    private String activeFlag;

    @Column(name = "IS_AUTHORIZED")
    private String isAuthorized;

    @Column(name = "is_two_fa_enabled", nullable = true)
    private Boolean is2FaEnabled;

    @Column(name = "two_fa_key", nullable = true)
    private String twoFaKey;

    @Column(name = "is_two_fa_configured", nullable = true)
    private Boolean is2FaConfigured;

    @ManyToOne
    @JoinColumn(name = "status_id", nullable = false)
    private StatusEntity statusEntity;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role", joinColumns = {@JoinColumn(name = "user_id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<RoleEntity> roles = new HashSet<RoleEntity>();
    
    /* (non-Javadoc)
     * @see org.openkilda.entity.BaseEntity#id()
     */
    @Override
    public Long id() {
        return userId;
    }

    /**
     * Gets the roles.
     *
     * @return the roles
     */
    public Set<RoleEntity> getRoles() {
        return roles;
    }

    /**
     * Sets the roles.
     *
     * @param roles the new roles
     */
    public void setRoles(final Set<RoleEntity> roles) {
        this.roles = roles;
    }

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
     * Gets the login time.
     *
     * @return the login time
     */
    public Date getLoginTime() {
        return loginTime;
    }

    /**
     * Sets the login time.
     *
     * @param loginTime the new login time
     */
    public void setLoginTime(final Date loginTime) {
        this.loginTime = loginTime;
    }

    /**
     * Gets the logout time.
     *
     * @return the logout time
     */
    public Date getLogoutTime() {
        return logoutTime;
    }

    /**
     * Sets the logout time.
     *
     * @param logoutTime the new logout time
     */
    public void setLogoutTime(final Date logoutTime) {
        this.logoutTime = logoutTime;
    }

    /**
     * Gets the active flag.
     *
     * @return the active flag
     */
    public Boolean getActiveFlag() {
        return activeFlag.equalsIgnoreCase("true") ? true : false;
    }

    /**
     * Sets the active flag.
     *
     * @param activeFlag the new active flag
     */
    public void setActiveFlag(final Boolean activeFlag) {
        this.activeFlag = activeFlag.toString();
    }

    /**
     * Sets the active flag.
     *
     * @param activeFlag the new active flag
     */
    public void setActiveFlag(final String activeFlag) {
        this.activeFlag = activeFlag;
    }

    /**
     * Gets the checks if is authorized.
     *
     * @return the checks if is authorized
     */
    public Boolean getIsAuthorized() {
        return isAuthorized.equalsIgnoreCase("true") ? true : false;
    }

    /**
     * Sets the checks if is authorized.
     *
     * @param isAuthorized the new checks if is authorized
     */
    public void setIsAuthorized(final Boolean isAuthorized) {
        this.isAuthorized = isAuthorized.toString();
    }

    /**
     * Sets the checks if is authorized.
     *
     * @param isAuthorized the new checks if is authorized
     */
    public void setIsAuthorized(final String isAuthorized) {
        this.isAuthorized = isAuthorized;
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
     * Sets the status entity.
     *
     * @param statusEntity the new status entity
     */
    public void setStatusEntity(final StatusEntity statusEntity) {
        this.statusEntity = statusEntity;
    }

    /**
     * Gets the checks if is 2 fa enabled.
     *
     * @return the checks if is 2 fa enabled
     */
    public boolean getIs2FaEnabled() {
        return is2FaEnabled;
    }

    /**
     * Sets the checks if is 2 fa enabled.
     *
     * @param is2FaEnabled the new checks if is 2 fa enabled
     */
    public void setIs2FaEnabled(final boolean is2FaEnabled) {
        this.is2FaEnabled = is2FaEnabled;
    }

    /**
     * Gets the checks if is 2 fa configured.
     *
     * @return the checks if is 2 fa configured
     */
    public boolean getIs2FaConfigured() {
        return is2FaConfigured;
    }

    /**
     * Sets the checks if is 2 fa configured.
     *
     * @param is2FaConfigured the new checks if is 2 fa configured
     */
    public void setIs2FaConfigured(final boolean is2FaConfigured) {
        this.is2FaConfigured = is2FaConfigured;
    }

    /**
     * Gets the two fa key.
     *
     * @return the two fa key
     */
    public String getTwoFaKey() {
        return twoFaKey;
    }

    /**
     * Sets the two fa key.
     *
     * @param twoFaKey the new two fa key
     */
    public void setTwoFaKey(final String twoFaKey) {
        this.twoFaKey = twoFaKey;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "UserEntity [userId=" + userId + ", username=" + username + ", name=" + name + ", email=" + email
                + ", loginTime=" + loginTime + ", logoutTime=" + logoutTime + ", activeFlag=" + activeFlag
                + ", isAuthorized=" + isAuthorized + ", statusEntity=" + statusEntity + ", roles=" + roles + "]";
    }
}
