/* Copyright 2020 Telstra Open Source
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

package org.openkilda.saml.entity;

import org.openkilda.constants.IConstants.IdpProviderType;
import org.openkilda.entity.BaseEntity;

import org.usermanagement.dao.entity.RoleEntity;

import java.io.Serializable;
import java.sql.Blob;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

/**
 * The Class SamlConfig.
 */

@Entity
@Table(name = "SAML_CONFIG")
public class SamlConfig extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "idp_id")
    private String idpId;
    
    @Column(name = "idp_name")
    private String idpName;
    
    @Column(name = "idp_url")
    private String idpUrl;
    
    @Column(name = "idp_attribute")
    private String idpAttribute;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "idp_provider_type")
    private IdpProviderType idpProviderType;
    
    @Column(name = "idp_metadata")
    @Lob @Basic(fetch = FetchType.LAZY)
    private Blob idpMetadata;
    
    @Column(name = "allow_user_creation")
    private boolean allowUserCreation;
    
    @Column(name = "active_status")
    private boolean activeStatus;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "saml_user_role", joinColumns = {@JoinColumn(name = "id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<RoleEntity> roles = new HashSet<RoleEntity>();

    @Override
    public Long id() {
        return id;
    }

    public String getIdpAttribute() {
        return idpAttribute;
    }

    public void setIdpAttribute(String idpAttribute) {
        this.idpAttribute = idpAttribute;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEntity> roles) {
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdpId() {
        return idpId;
    }

    public void setIdpId(String idpId) {
        this.idpId = idpId;
    }

    public IdpProviderType getIdpProviderType() {
        return idpProviderType;
    }

    public void setIdpProviderType(IdpProviderType idpProviderType) {
        this.idpProviderType = idpProviderType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getIdpName() {
        return idpName;
    }

    public void setIdpName(String idpName) {
        this.idpName = idpName;
    }

    public String getIdpUrl() {
        return idpUrl;
    }

    public void setIdpUrl(String idpUrl) {
        this.idpUrl = idpUrl;
    }

    public Blob getIdpMetadata() {
        return idpMetadata;
    }

    public void setIdpMetadata(Blob idpMetadata) {
        this.idpMetadata = idpMetadata;
    }

    public boolean isAllowUserCreation() {
        return allowUserCreation;
    }

    public void setAllowUserCreation(boolean allowUserCreation) {
        this.allowUserCreation = allowUserCreation;
    }

    public boolean isActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(boolean activeStatus) {
        this.activeStatus = activeStatus;
    }

    @Override
    public String toString() {
        return "SamlConfig [id=" + id + ", idp_name=" + idpName + ", idp_url=" + idpUrl + "]";
    }
}
