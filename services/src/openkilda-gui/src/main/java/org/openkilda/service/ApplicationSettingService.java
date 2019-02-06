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
import org.openkilda.constants.IConstants.ApplicationSetting;
import org.openkilda.constants.IConstants.StorageType;
import org.openkilda.dao.entity.ApplicationSettingEntity;
import org.openkilda.dao.repository.ApplicationSettingRepository;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("applicationSettingService")
public class ApplicationSettingService {

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

    @Autowired
    private ActivityLogger activityLogger;

    /**
     * Gets the application settings.
     *
     * @return the application settings
     */
    public Map<String, String> getApplicationSettings() {
        Map<String, String> settings = Arrays.asList(IConstants.ApplicationSetting.values()).stream()
                .collect(Collectors.toMap(s -> s.name(), s -> s.getValue()));

        List<ApplicationSettingEntity> applicationSettings = applicationSettingRepository.findAll();
        settings.putAll(applicationSettings.stream().collect(Collectors.toMap(
                setting -> setting.getSettingType().toUpperCase(), setting -> setting.getValue().toUpperCase())));
        return settings;
    }

    /**
     * Gets the application setting.
     *
     * @param applicationSetting
     *            the application settings
     * @return the application setting
     */
    public String getApplicationSetting(final ApplicationSetting applicationSetting) {
        ApplicationSettingEntity applicationSettingEntity = applicationSettingRepository
                .findBySettingTypeIgnoreCase(applicationSetting.name());
        return applicationSettingEntity == null ? applicationSetting.getValue() : applicationSettingEntity.getValue();
    }

    /**
     * Save or update application setting.
     *
     * @param applicationSetting the application setting
     * @param value the value
     */
    public void saveOrUpdateApplicationSetting(final ApplicationSetting applicationSetting, final String value) {
        ApplicationSettingEntity applicationSettingEntity = applicationSettingRepository
                .findBySettingTypeIgnoreCase(applicationSetting.name());
        if (applicationSettingEntity == null) {
            applicationSettingEntity = new ApplicationSettingEntity(applicationSetting.name());
        }

        if (applicationSetting == ApplicationSetting.SESSION_TIMEOUT) {
            IConstants.SessionTimeout.TIME_IN_MINUTE = Integer.valueOf(value);
            activityLogger.log(ActivityType.CONFIG_SESSION_TIMEOUT, value);
        } else if (applicationSetting == ApplicationSetting.SWITCH_NAME_STORAGE_TYPE) {
            IConstants.STORAGE_TYPE_FOR_SWITCH_NAME = StorageType.get(value);
            activityLogger.log(ActivityType.CONFIG_SWITCH_NAME_STORAGE_TYPE, value);
        }
        applicationSettingEntity.setValue(value);
        applicationSettingEntity.setUpdatedDate(new Date());
        applicationSettingRepository.save(applicationSettingEntity);
    }
}
