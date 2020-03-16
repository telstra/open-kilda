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

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "kilda_auth_type")
@Data
@NoArgsConstructor
public class AuthTypeEntity {

    @Id
    @Column(name = "auth_type_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer authTypeId;

    @Column(name = "auth_type_name", nullable = false)
    private String authTypeName;
    
    @Column(name = "auth_type_code", nullable = false)
    private String authTypeCode;
    
}
