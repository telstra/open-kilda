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

package org.openkilda.controller;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.IConstants.ApplicationSetting;
import org.openkilda.constants.IConstants.StorageType;
import org.openkilda.service.ApplicationSettingService;
import org.openkilda.validator.ApplicationSettingsValidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * The Class SessionTimeoutController.
 *
 */

@RestController
@RequestMapping(value = "/api/settings")
public class ApplicationSettingController extends BaseController {

    @Autowired
    private ApplicationSettingService applicationSettingService;

    @Autowired
    private ApplicationSettingsValidator applicationSettingsValidator;

    /**
     * Gets the application settings.
     *
     * @return the application settings
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public Map<String, String> getApplicationSettings() {
        return applicationSettingService.getApplicationSettings();
    }

    /**
     * Gets the storage types.
     *
     * @return the storage types
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/storagetypes", method = RequestMethod.GET)
    public StorageType[] getStorageTypes() {
        return IConstants.StorageType.values();
    }

    /**
     * Save or update application setting.
     *
     * @param type the type
     * @param value the value
     * @param request the request
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{type}", method = RequestMethod.PATCH)
    @Permissions(values = { IConstants.Permission.APPLICATION_SETTING })
    public void saveOrUpdateApplicationSetting(@PathVariable("type") ApplicationSetting type, @RequestBody String value,
            HttpServletRequest request) {

        applicationSettingsValidator.validate(type, value);
        value = value.trim().toUpperCase();
        applicationSettingService.saveOrUpdateApplicationSetting(type, value);
        
        if (type == ApplicationSetting.SESSION_TIMEOUT) {
            request.getSession().setMaxInactiveInterval(Integer.valueOf(value) * 60);
        }
    }
}
