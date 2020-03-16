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

import org.openkilda.model.response.Error;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The Class AuthPropertyService.
 */
@Component
public class AuthPropertyService {

    public static final String CODE = ".code";
    public static final String MESSAGE = ".message";

    @Autowired
    private Environment authMessages;

    /**
     * Returns error code and message from the properties file.
     *
     * @param errorMsg whose code and message are requested.
     * @return error message and code.
     */
    public Error getError(final String errorMsg) {
        String errorMessageCode = authMessages.getProperty(errorMsg + CODE);
        String errorMessage = authMessages.getProperty(errorMsg + MESSAGE);
        return new Error(Integer.valueOf(errorMessageCode), errorMessage);
    }

    /**
     * Gets the message.
     *
     * @param msg the msg
     * @return the message
     */
    public String getMessage(final String msg) {
        return authMessages.getProperty(msg);
    }
}
