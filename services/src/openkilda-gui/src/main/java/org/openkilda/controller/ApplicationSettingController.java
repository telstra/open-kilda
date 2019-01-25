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
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.SwitchNameStorageType;
import org.openkilda.service.ApplicationSettingService;
import org.openkilda.utility.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.usermanagement.controller.UserController;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * The Class SessionTimeoutController.
 *
 */

@RestController
@RequestMapping(value = "/api/settings")
public class ApplicationSettingController extends BaseController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private ApplicationSettingService applicationSettingService;
    
    @Autowired
    private ActivityLogger activityLogger;
    
    @Autowired
    private MessageUtils messageUtil;
    
    /**
     * Gets the user settings.
     *
     * @return the user settings
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/sessiontimeout", method = RequestMethod.GET)
    @Permissions(values = { IConstants.Permission.SESSION_TIMEOUT_SETTING })
    public int getSessioTimeout() {
        return applicationSettingService.getSessionTimeout();
    }

    /**
     * Save or update settings.
     *
     * @param sessionTimeoutInMinutes the sessionTimeoutInMinutes
     * @return the string
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/sessiontimeout", method = RequestMethod.PATCH)
    @Permissions(values = { IConstants.Permission.SESSION_TIMEOUT_SETTING })
    public int saveOrUpdateSessioTimeout(@RequestBody final int sessionTimeoutInMinutes, HttpServletRequest request) {
        LOGGER.info("[saveOrUpdateSessioTimeout] (sessionTimeoutInMinutes: " + sessionTimeoutInMinutes + ")");
        if (sessionTimeoutInMinutes < 1) {
            throw new RequestValidationException(
                    messageUtil.getAttributeNotvalid(IConstants.ApplicationSetting.SESSION_TIMEOUT));
        }
        activityLogger.log(ActivityType.CONFIG_SESSION_TIMEOUT, String.valueOf(sessionTimeoutInMinutes));
        applicationSettingService.updateSessionTimeout(sessionTimeoutInMinutes);
        request.getSession().setMaxInactiveInterval(sessionTimeoutInMinutes * 60);
        return sessionTimeoutInMinutes;
    }
    
    /**
     * Gets the switch name storage types.
     *
     * @return the switch name storage types
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/switchnamestoragetype/list", method = RequestMethod.GET)
    @Permissions(values = { IConstants.Permission.SWITCH_NAME_STORAGE_TYPE })
    public List<String> getSwitchNameStorageTypes() {
        return Arrays.asList(IConstants.SwitchNameStorageType.TYPES);
    }
    
    /**
     * Gets the switch name storage type.
     *
     * @return the switch name storage type
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/switchnamestoragetype", method = RequestMethod.GET)
    @Permissions(values = { IConstants.Permission.SWITCH_NAME_STORAGE_TYPE })
    public SwitchNameStorageType getSwitchNameStorageType() {
        return applicationSettingService.getSwitchNameStorageType();
    }
    
    /**
     * Save or update switch name storage type.
     *
     * @param switchNameStorageType the switch name storage type
     * @param request the request
     * @return the string
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/switchnamestoragetype", method = RequestMethod.PATCH)
    @Permissions(values = { IConstants.Permission.SWITCH_NAME_STORAGE_TYPE })
    public SwitchNameStorageType saveOrUpdateSwitchNameStorageType(@RequestBody final String switchNameStorageType,
            HttpServletRequest request) {
        LOGGER.info("[saveOrUpdateSwitchNameStorageType] (switchNameStorageType: " + switchNameStorageType + ")");
        if (StringUtil.isNullOrEmpty(switchNameStorageType) || !Arrays.asList(IConstants.SwitchNameStorageType.TYPES)
                .contains(switchNameStorageType.trim().toUpperCase())) {
            throw new RequestValidationException(
                    messageUtil.getAttributeNotvalid(IConstants.ApplicationSetting.SWITCH_NAME_STORAGE_TYPE));
        }
        activityLogger.log(ActivityType.CONFIG_SWITCH_NAME_STORAGE_TYPE, switchNameStorageType);
        applicationSettingService.updateSwitchNameStorageType(switchNameStorageType);
        return new SwitchNameStorageType(switchNameStorageType);
    }
}
