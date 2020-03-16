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

package org.openkilda.store.controller;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.store.controller.validator.OauthTwoConfigValidator;
import org.openkilda.store.model.AuthTypeDto;
import org.openkilda.store.model.OauthTwoConfigDto;
import org.openkilda.store.service.AuthService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * The Class AuthController.
 */

@Controller
@RequestMapping(value = "/api/auth")
public class AuthController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private ActivityLogger activityLogger;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private OauthTwoConfigValidator oauthTwoConfigValidator;
    
    /**
     * Gets the auths.
     *
     * @return the auths
     */
    @RequestMapping(value = "/types", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<AuthTypeDto> getAuths() {
        return authService.getAuthTypes();
    }
    
    /**
     * Gets the oauth two config.
     *
     * @return the oauth two config
     */
    @RequestMapping(value = "/oauth-two-config", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody OauthTwoConfigDto getOauthTwoConfig() {
        LOGGER.info("Get oauth two configuration");
        return authService.getOauthConfig();
    }
    
    /**
     * Save or update oauth two config.
     *
     * @param oauthTwoConfigDto the oauth two config dto
     * @return the oauth two config dto
     */
    @RequestMapping(value = "/oauth-two-config/save", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.STORE_SETTING })
    public @ResponseBody OauthTwoConfigDto saveOrUpdateOauthTwoConfig(
            @RequestBody OauthTwoConfigDto oauthTwoConfigDto) {
        LOGGER.info("Save or update store Urls. linkStoreConfigDto: " + oauthTwoConfigDto.toString());
        activityLogger.log(ActivityType.UPDATE_OAUTH_CONFIG, oauthTwoConfigDto.getUsername());
        oauthTwoConfigValidator.validate(oauthTwoConfigDto);
        return authService.saveOrUpdateOauthConfig(oauthTwoConfigDto);
    }
}
