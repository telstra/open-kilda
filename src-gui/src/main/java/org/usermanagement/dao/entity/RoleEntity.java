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
import org.openkilda.saml.dao.entity.SamlConfigEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "KILDA_ROLE")
public class RoleEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = -57044334239698601L;

    @Id
    @Column(name = "role_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    @Column(name = "role", nullable = false)
    private String name;

    @Column(name = "description", nullable = true)
    private String description;

    @ManyToOne
    @JoinColumn(name = "status_id", nullable = false)
    private StatusEntity statusEntity;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "ROLE_PERMISSION", joinColumns = {@JoinColumn(name = "role_id")}, inverseJoinColumns = {
            @JoinColumn(name = "permission_id")})
    private Set<PermissionEntity> permissions = new HashSet<PermissionEntity>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "USER_ROLE", joinColumns = {@JoinColumn(name = "role_id")}, inverseJoinColumns = {
            @JoinColumn(name = "user_id")})
    private Set<UserEntity> users = new HashSet<UserEntity>();


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "SAML_USER_ROLES", joinColumns = {@JoinColumn(name = "role_id")}, inverseJoinColumns = {
            @JoinColumn(name = "id")})
    private Set<SamlConfigEntity> samlUsers = new HashSet<SamlConfigEntity>();

    /* (non-Javadoc)
     * @see org.openkilda.entity.BaseEntity#id()
     */
    @Override
    public Long id() {
        return roleId;
    }

    public Set<SamlConfigEntity> getSamlUsers() {
        return samlUsers;
    }

    public void setSamlUsers(Set<SamlConfigEntity> samlUsers) {
        this.samlUsers = samlUsers;
    }

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
    public void setRoleId(final Long roleId) {
        this.roleId = roleId;
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
     * Gets the permissions.
     *
     * @return the permissions
     */
    public Set<PermissionEntity> getPermissions() {
        return permissions;
    }

    /**
     * Sets the permissions.
     *
     * @param permissions the new permissions
     */
    public void setPermissions(final Set<PermissionEntity> permissions) {
        this.permissions = permissions;
    }

    public StatusEntity getStatusEntity() {
        return statusEntity;
    }

    public void setStatusEntity(final StatusEntity statusEntity) {
        this.statusEntity = statusEntity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public Set<UserEntity> getUsers() {
        return users;
    }

    public void setUsers(Set<UserEntity> users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return "RoleEntity [roleId=" + roleId + ", name=" + name + ", description=" + description + ", statusEntity="
                + statusEntity + ", permissions=" + permissions + "]";
    }

}
