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

import java.util.List;
import java.util.Map.Entry;

import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.constants.StoreUrl;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.utility.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

/**
 * The Class LinkStoreConfigValidator.
 */

@Component
public class LinkStoreConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(OauthTwoConfigValidator.class);
    
    @Autowired
    private MessageUtils messageUtil;

    /**
     * Validate.
     *
     * @param linkStoreConfigDto the link store config dto
     */
    public void validate(final LinkStoreConfigDto linkStoreConfigDto) {
        List<String> urls = StoreUrl.getUrlName(StoreType.LINK_STORE.getCode());
        for (Entry<String, UrlDto> urlEntrySet : linkStoreConfigDto.getUrls().entrySet()) {
            if (!urls.contains(urlEntrySet.getKey())) {
                LOGGER.error("Validation fail for link store configuration. Error: "
                        + messageUtil.getAttributeNotvalid(urlEntrySet.getKey()));
                throw new RequestValidationException(messageUtil.getAttributeNotvalid(urlEntrySet.getKey()));
            } else if (ValidatorUtil.isNull(urlEntrySet.getValue().getUrl())) {
                LOGGER.error("Validation fail for link store configuration. Error: "
                        + messageUtil.getAttributeNotNull("url of " + urlEntrySet.getKey()));
                throw new RequestValidationException(messageUtil.getAttributeNotNull(urlEntrySet.getKey()));
            } else if (ValidatorUtil.isNull(urlEntrySet.getValue().getMethodType())) {
                LOGGER.error("Validation fail for link store configuration. Error: "
                        + messageUtil.getAttributeNotNull("method-type of " + urlEntrySet.getKey()));
                throw new RequestValidationException(
                        messageUtil.getAttributeNotNull("method-type of " + urlEntrySet.getKey()));
            }
        }
    }
}
