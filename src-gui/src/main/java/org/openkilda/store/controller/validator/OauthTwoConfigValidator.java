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

import org.openkilda.store.model.OauthTwoConfigDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

/**
 * The Class OauthTwoConfigValidator.
 */

@Component
public class OauthTwoConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(OauthTwoConfigValidator.class);

    @Autowired
    private MessageUtils messageUtil;

    /**
     * Validate.
     *
     * @param oauthTwoConfigDto the oauth two config dto
     */
    public void validate(final OauthTwoConfigDto oauthTwoConfigDto) {
        if (ValidatorUtil.isNull(oauthTwoConfigDto.getUsername())) {
            LOGGER.warn("Validation fail for oauth two configuration. Error: "
                    + messageUtil.getAttributeNotNull("username"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("username"));
        } else if (ValidatorUtil.isNull(oauthTwoConfigDto.getPassword())) {
            LOGGER.warn("Validation fail for oauth two configuration. Error: "
                    + messageUtil.getAttributeNotNull("password"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("password"));
        } else if (ValidatorUtil.isNull(oauthTwoConfigDto.getOauthGenerateTokenUrl().getUrl())) {
            LOGGER.warn("Validation fail for oauth two configuration. Error: "
                    + messageUtil.getAttributeNotNull("oauth-generate-token-url"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("oauth-generate-token-url"));
        } else if (ValidatorUtil.isNull(oauthTwoConfigDto.getOauthRefreshTokenUrl().getUrl())) {
            LOGGER.warn("Validation fail for oauth two configuration. Error: "
                    + messageUtil.getAttributeNotNull("oauth-refresh-token-url"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("oauth-refresh-token-url"));
        } else if (ValidatorUtil.isNull(oauthTwoConfigDto.getOauthGenerateTokenUrl().getMethodType())) {
            LOGGER.warn("Validation fail for oauth two configuration. Error: "
                    + messageUtil.getAttributeNotNull("method-type of oauth-generate-token-url"));
            throw new RequestValidationException(
                    messageUtil.getAttributeNotNull("method-type of oauth-generate-token-url"));
        } else if (ValidatorUtil.isNull(oauthTwoConfigDto.getOauthRefreshTokenUrl().getMethodType())) {
            LOGGER.warn("Validation fail for oauth two configuration. Error: "
                    + messageUtil.getAttributeNotNull("method-type of oauth-refresh-token-url"));
            throw new RequestValidationException(
                    messageUtil.getAttributeNotNull("method-type of oauth-refresh-token-url"));
        }
    }
}
