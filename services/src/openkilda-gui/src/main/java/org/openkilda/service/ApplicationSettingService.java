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

package org.openkilda.service;

import org.openkilda.constants.IConstants;
import org.openkilda.dao.entity.ApplicationSettingEntity;
import org.openkilda.dao.repository.ApplicationSettingRepository;
import org.openkilda.utility.CollectionUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service("applicationSettingService")
public class ApplicationSettingService {

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

    /**
     * Update session timeout.
     *
     * @param sessionTimeoutInMinutes the session timeout in minutes
     */
    public void updateSessionTimeout(final int sessionTimeoutInMinutes) {
        List<ApplicationSettingEntity> applicationSettings = applicationSettingRepository
                .findBySettingType(IConstants.ApplicationSetting.SESSION_TIMEOUT);
        ApplicationSettingEntity applicationSetting = new ApplicationSettingEntity();
        if (!CollectionUtil.isEmpty(applicationSettings)) {
            applicationSetting = applicationSettings.get(0);
        }
        applicationSetting.setSettingType(IConstants.ApplicationSetting.SESSION_TIMEOUT);
        applicationSetting.setValue(String.valueOf(sessionTimeoutInMinutes));
        applicationSetting.setUpdatedDate(new Date());
        applicationSettingRepository.save(applicationSetting);
        IConstants.SessionTimeout.TIME_IN_MINUTE = sessionTimeoutInMinutes;
    }
    
    /**
     * Gets the session timeout.
     *
     * @return the session timeout
     */
    public int getSessionTimeout() {
        List<ApplicationSettingEntity> applicationSettings = applicationSettingRepository
                .findBySettingType(IConstants.ApplicationSetting.SESSION_TIMEOUT);
        if (!CollectionUtil.isEmpty(applicationSettings)) {
            return Integer.valueOf(applicationSettings.get(0).getValue());
        }
        return IConstants.SessionTimeout.DEFAULT_TIME_IN_MINUTE;
    }
}
