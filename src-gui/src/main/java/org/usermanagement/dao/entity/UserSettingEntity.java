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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;

/**
 * The Class UserSettingEntity.
 */

@Entity
@Table(name = "KILDA_USER_SETTING")
public class UserSettingEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "USER_SETTING_ID", nullable = false)
    @GeneratedValue(generator = "increment")
    @GenericGenerator(name = "increment", strategy = "increment")
    private Long userSettingId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "SETTINGS", nullable = false)
    @Lob
    private String settings;

    @Column(name = "DATA", nullable = true, columnDefinition = "blob")
    @Lob
    private String data;

    /* (non-Javadoc)
     * @see org.openkilda.entity.BaseEntity#id()
     */
    @Override
    public Long id() {
        return userSettingId;
    }

    public Long getUserSettingId() {
        return userSettingId;
    }

    public void setUserSettingId(Long userSettingId) {
        this.userSettingId = userSettingId;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getData() {
        return data;
    }

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
        return "UserEntity [userSettingId=" + userSettingId + ", settings=" + settings + ", userId" + userId + "]";
    }
}

