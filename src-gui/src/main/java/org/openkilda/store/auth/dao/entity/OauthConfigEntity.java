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

package org.openkilda.store.auth.dao.entity;

import org.openkilda.entity.BaseEntity;
import org.openkilda.store.common.dao.entity.UrlEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "kilda_oauth_config")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class OauthConfigEntity extends BaseEntity {

    @Id
    @Column(name = "oauth_config_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer oauthConfigId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "generate_token_id", nullable = false)
    private UrlEntity generateToken = new UrlEntity();

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "refresh_token_id", nullable = false)
    private UrlEntity refreshToken = new UrlEntity();

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "auth_type_id", nullable = false)
    private AuthTypeEntity authType;

    @Override
    public Long id() {
        return oauthConfigId != null ? Long.valueOf(oauthConfigId) : null;
    }
}
