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

package org.usermanagement.service;

import java.util.Map;

/**
 * The service for making string from template by the model.
 */
public interface TemplateService {

    /**
     * Take a result string from template and provided model.
     *
     * @param template template.
     * @param model Map with model values.
     * @return result string.
     */
    String mergeTemplateToString(Template template, Map<String, Object> model);

    /**
     * Enum of templates.
     */
    enum Template {
        RESET_ACCOUNT_PASSWORD, ACCOUNT_USERNAME, ACCOUNT_PASSWORD, RESET_2FA, CHANGE_PASSWORD;
    }
}
