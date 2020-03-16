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

package org.openkilda.store.common.dao.entity;

import org.openkilda.store.auth.dao.entity.OauthConfigEntity;

import lombok.Data;
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
@Table(name = "kilda_store_type")
@Data
@NoArgsConstructor
public class StoreTypeEntity {

    @Id
    @Column(name = "store_type_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer storeTypeId;

    @Column(name = "store_type_name", nullable = false)
    private String storeTypeName;
    
    @Column(name = "store_type_code", nullable = false)
    private String storeTypeCode;
    
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "oauth_config_id")
    private OauthConfigEntity oauthConfigEntity;
}
