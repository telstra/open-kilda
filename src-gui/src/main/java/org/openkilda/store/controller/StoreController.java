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
import org.openkilda.store.controller.validator.LinkStoreConfigValidator;
import org.openkilda.store.controller.validator.SwitchStoreConfigValidator;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.model.StoreTypeDto;
import org.openkilda.store.model.SwitchStoreConfigDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.StoreService;

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
import java.util.Map.Entry;

/**
 * The Class StoreController.
 */

@Controller
@RequestMapping(value = "/api/store")
public class StoreController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreController.class);

    @Autowired
    private ActivityLogger activityLogger;

    @Autowired
    private StoreService storeService;

    @Autowired
    private LinkStoreConfigValidator linkStoreConfigValidator;

    @Autowired
    private SwitchStoreConfigValidator switchStoreConfigValidator;

    /**
     * Gets the stores.
     *
     * @return the stores
     */
    @RequestMapping(value = "/types", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<StoreTypeDto> getStores() {
        return storeService.getStoreTypes();
    }

    /**
     * Gets the link store config.
     *
     * @return the link store config
     */
    @RequestMapping(value = "/link-store-config", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody LinkStoreConfigDto getLinkStoreConfig() {
        LOGGER.info("Get link store config");
        return storeService.getLinkStoreConfig();
    }

    /**
     * Save or update link store config.
     *
     * @param linkStoreConfigDto the link store config dto
     * @return the link store config dto
     */
    @RequestMapping(value = "/link-store-config/save", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.STORE_SETTING })
    public @ResponseBody LinkStoreConfigDto saveOrUpdateLinkStoreConfig(
            @RequestBody LinkStoreConfigDto linkStoreConfigDto) {
        LOGGER.info("Save or update link store configuration. linkStoreConfigDto: " + linkStoreConfigDto);
        StringBuilder key = new StringBuilder();
        for (Entry<String, UrlDto> urlEntrySet : linkStoreConfigDto.getUrls().entrySet()) {
            key = (key.length() > 0) ? key.append("\n," + urlEntrySet.getKey()) : key.append(urlEntrySet.getKey());
        }
        activityLogger.log(ActivityType.UPDATE_LINK_STORE_CONFIG, key.toString());
        linkStoreConfigValidator.validate(linkStoreConfigDto);
        return storeService.saveOrUpdateLinkStoreConfig(linkStoreConfigDto);
    }

    /**
     * Delete link store config.
     *
     * @return true, if successful
     */
    @RequestMapping(value = "/link-store-config/delete", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.STORE_SETTING })
    @ResponseBody
    public boolean deleteLinkStoreConfig() {
        LOGGER.info("Delete link store configuration");
        activityLogger.log(ActivityType.DELETE_LINK_STORE_CONFIG);
        return storeService.deleteLinkStoreConfig();
    }

    /**
     * Save or update switch store config.
     *
     * @param switchStoreConfigDto the switch store config dto
     * @return the switch store config dto
     */
    @RequestMapping(value = "/switch-store-config/save", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.STORE_SETTING })
    public @ResponseBody SwitchStoreConfigDto saveOrUpdateSwitchStoreConfig(
            @RequestBody SwitchStoreConfigDto switchStoreConfigDto) {
        LOGGER.info("Save or update switch store configuration. switchStoreConfigDto: " + switchStoreConfigDto);
        StringBuilder key = new StringBuilder();
        for (Entry<String, UrlDto> urlEntrySet : switchStoreConfigDto.getUrls().entrySet()) {
            key = (key.length() > 0) ? key.append("\n," + urlEntrySet.getKey()) : key.append(urlEntrySet.getKey());
        }
        activityLogger.log(ActivityType.UPDATE_SWITCH_STORE_CONFIG, key.toString());
        switchStoreConfigValidator.validate(switchStoreConfigDto);
        return storeService.saveOrUpdateSwitchStoreConfig(switchStoreConfigDto);
    }

    /**
     * Delete switch store config.
     *
     * @return true, if successful
     */
    @RequestMapping(value = "/switch-store-config/delete", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.STORE_SETTING })
    @ResponseBody
    public boolean deleteSwitchStoreConfig() {
        LOGGER.info("Delete link store configuration.");
        activityLogger.log(ActivityType.DELETE_SWITCH_STORE_CONFIG);
        return storeService.deleteSwitchStoreConfig();
    }

    /**
     * Gets the switch store config.
     *
     * @return the switch store config
     */
    @RequestMapping(value = "/switch-store-config", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody SwitchStoreConfigDto getSwitchStoreConfig() {
        LOGGER.info("Get switch store config");
        return storeService.getSwitchStoreConfig();
    }
}
