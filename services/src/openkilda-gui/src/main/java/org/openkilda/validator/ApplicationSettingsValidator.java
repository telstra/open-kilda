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

package org.openkilda.validator;

import org.openkilda.constants.IConstants.ApplicationSetting;
import org.openkilda.constants.IConstants.StorageType;
import org.openkilda.utility.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;

import java.util.Arrays;
import java.util.List;

/**
 * The Class ApplicationSettingsValidator.
 */

@Component
public class ApplicationSettingsValidator {

    @Autowired
    private MessageUtils messageUtil;
    
    /**
     * Validate.
     *
     * @param type the type
     * @param value the value
     */
    public void validate(final ApplicationSetting type, final String value) {
        if (type == null || StringUtil.isNullOrEmpty(value)) {
            throw new RequestValidationException(messageUtil.getAttributeNotvalid("setting type"));
        } else if (type == ApplicationSetting.SESSION_TIMEOUT) {
            try {
                if (Integer.valueOf(value) < 1) {
                    throw new RequestValidationException(
                            messageUtil.getAttributeNotvalid(ApplicationSetting.SESSION_TIMEOUT.name()));
                }
            } catch (NumberFormatException ex) {
                throw new RequestValidationException(
                        messageUtil.getAttributeNotvalid(ApplicationSetting.SESSION_TIMEOUT.name()));
            }
        } else if (type == ApplicationSetting.SWITCH_NAME_STORAGE_TYPE) {
            List<StorageType> storageTypes = Arrays.asList(StorageType.values());
            
            if (!storageTypes.stream().anyMatch(e -> e.name().equalsIgnoreCase(value.trim()))) {
                throw new RequestValidationException(messageUtil
                        .getAttributeNotvalid(ApplicationSetting.SWITCH_NAME_STORAGE_TYPE.name()));
            }
        }
    }
}
