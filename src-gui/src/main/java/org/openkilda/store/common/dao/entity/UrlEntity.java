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

import org.openkilda.entity.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "kilda_urls")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UrlEntity extends BaseEntity {

    @Id
    @Column(name = "url_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer urlId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "method_type", nullable = false)
    private String methodType;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "header")
    private String header;

    @Column(name = "body")
    private String body;

    @Override
    public Long id() {
        return urlId != null ? Long.valueOf(urlId) : null;
    }
}
