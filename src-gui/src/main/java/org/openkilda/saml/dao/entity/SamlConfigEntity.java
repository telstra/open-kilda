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

package org.openkilda.saml.dao.entity;

import org.openkilda.constants.IConstants.ProviderType;
import org.openkilda.entity.BaseEntity;

import lombok.Data;

import org.hibernate.annotations.GenericGenerator;

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
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

/**
 * The Class SamlConfiguration.
 */

@Entity
@Table(name = "SAML_CONFIGURATION")
@Data
public class SamlConfigEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "increment")
    @GenericGenerator(name = "increment", strategy = "increment")
    private Long id;

    @Column(name = "uuid")
    private String uuid;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "url")
    private String url;
    
    @Column(name = "attribute")
    private String attribute;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "type")
    private ProviderType type;
    
    @Column(name = "metadata")
    @Lob @Basic(fetch = FetchType.LAZY)
    private Blob metadata;
    
    @Column(name = "user_creation")
    private boolean userCreation;
    
    @Column(name = "status")
    private boolean status;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "SAML_USER_ROLES", joinColumns = {@JoinColumn(name = "id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<RoleEntity> roles = new HashSet<RoleEntity>();

    @Override
    public Long id() {
        return id;
    }

    @Override
    public String toString() {
        return "SamlConfig [id=" + id + ", name=" + name + ", url=" + url + "]";
    }
}
