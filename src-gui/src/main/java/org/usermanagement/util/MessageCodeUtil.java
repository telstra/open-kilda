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

package org.usermanagement.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:message.properties")
public class MessageCodeUtil {

    @Value("${error.code.prefix}")
    private String errorCodePrefix;

    @Value("${attribute.not.found.code}")
    private String attributeNotFoundCode;

    /**
     * Gets the attribute not found code.
     *
     * @return the attribute not found code
     */
    public int getAttributeNotFoundCode() {
        return Integer.parseInt(errorCodePrefix + attributeNotFoundCode);
    }
}
