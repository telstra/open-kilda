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
import java.sql.Timestamp;
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
import javax.persistence.Table;

/**
 * The Class User.
 *
 * @author Gaurav Chugh
 */
@Entity
@Table(name = "kilda_user")
public class User extends BaseEntity implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The user id. */
    @Id
    @Column(name = "User_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long userId;

    /** The username. */
    @Column(name = "Username", nullable = false)
    private String username;

    /** The password. */
    @Column(name = "Password", nullable = false)
    private String password;

    /** The name. */
    @Column(name = "Name")
    private String name;

    /** The email. */
    @Column(name = "Email")
    private String email;

    /** The login time. */
    @Column(name = "LOGIN_TIME")
    private Timestamp loginTime;

    /** The logout time. */
    @Column(name = "Logout_Time")
    private Timestamp logoutTime;

    /** The active flag. */
    @Column(name = "Active_Flag")
    private Boolean activeFlag;

    /** The is authorized. */
    @Column(name = "Is_Authorized")
    private String isAuthorized;

    /** The roles. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role", joinColumns = {@JoinColumn(name = "user_id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<Role> roles = new HashSet<Role>();

    /**
     * Gets the roles.
     *
     * @return the roles
     */
    public Set<Role> getRoles() {
        return roles;
    }

    /**
     * Sets the roles.
     *
     * @param roles the new roles
     */
    public void setRoles(Set<Role> roles) {
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
    public void setUserId(Long userId) {
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
    public void setUsername(String username) {
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
    public void setPassword(String password) {
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
    public void setName(String name) {
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
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gets the login time.
     *
     * @return the login time
     */
    public Timestamp getLoginTime() {
        return loginTime;
    }

    /**
     * Sets the login time.
     *
     * @param loginTime the new login time
     */
    public void setLoginTime(Timestamp loginTime) {
        this.loginTime = loginTime;
    }

    /**
     * Gets the logout time.
     *
     * @return the logout time
     */
    public Timestamp getLogoutTime() {
        return logoutTime;
    }

    /**
     * Sets the logout time.
     *
     * @param logoutTime the new logout time
     */
    public void setLogoutTime(Timestamp logoutTime) {
        this.logoutTime = logoutTime;
    }

    /**
     * Gets the active flag.
     *
     * @return the active flag
     */
    public Boolean getActiveFlag() {
        return activeFlag;
    }

    /**
     * Sets the active flag.
     *
     * @param activeFlag the new active flag
     */
    public void setActiveFlag(Boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

    /**
     * Gets the checks if is authorized.
     *
     * @return the checks if is authorized
     */
    public String getIsAuthorized() {
        return isAuthorized;
    }

    /**
     * Sets the checks if is authorized.
     *
     * @param isAuthorized the new checks if is authorized
     */
    public void setIsAuthorized(String isAuthorized) {
        this.isAuthorized = isAuthorized;
    }

}
