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

package org.openkilda.store.controller.validator;

import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.auth.dao.entity.OauthConfigEntity;
import org.openkilda.store.auth.dao.repository.OauthConfigRepository;
import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.constants.StoreUrl;
import org.openkilda.store.model.SwitchStoreConfigDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.utility.CollectionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

import java.util.List;
import java.util.Map.Entry;


/**
 * The Class SwitchStoreConfigValidator.
 */

@Component
public class SwitchStoreConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchStoreConfigValidator.class);
    
    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private OauthConfigRepository oauthConfigRepository;
    

    /**
     * Validate.
     *
     * @param switchStoreConfigDto the link store config dto
     */
    public void validate(final SwitchStoreConfigDto switchStoreConfigDto) {
        List<OauthConfigEntity> oauthConfigEntityList = oauthConfigRepository
                .findByAuthType_authTypeId(AuthType.OAUTH_TWO.getAuthTypeEntity().getAuthTypeId());
        if (CollectionUtil.isEmpty(oauthConfigEntityList)) {
            LOGGER.warn(messageUtil.getStoreMustConfigured());
            throw new RequestValidationException(messageUtil.getStoreMustConfigured());
        }
        List<String> urls = StoreUrl.getUrlName(StoreType.SWITCH_STORE.getCode());

        for (Entry<String, UrlDto> urlEntrySet : switchStoreConfigDto.getUrls().entrySet()) {
            if (!urls.contains(urlEntrySet.getKey())) {
                LOGGER.warn("Validation fail for switch store configuration. Error: "
                        + messageUtil.getAttributeNotvalid(urlEntrySet.getKey()));
                throw new RequestValidationException(messageUtil.getAttributeNotvalid(urlEntrySet.getKey()));
            } else if (ValidatorUtil.isNull(urlEntrySet.getValue().getUrl())) {
                LOGGER.warn("Validation fail for switch store configuration. Error: "
                        + messageUtil.getAttributeNotNull("url of " + urlEntrySet.getKey()));
                throw new RequestValidationException(messageUtil.getAttributeNotNull(urlEntrySet.getKey()));
            } else if (ValidatorUtil.isNull(urlEntrySet.getValue().getMethodType())) {
                LOGGER.warn("Validation fail for switch store configuration. Error: "
                        + messageUtil.getAttributeNotNull("method-type of " + urlEntrySet.getKey()));
                throw new RequestValidationException(
                        messageUtil.getAttributeNotNull("method-type of " + urlEntrySet.getKey()));
            }
        }
    }
}
