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

package org.openkilda.config.provider;

import static java.util.Collections.emptySet;

import java.util.Set;

/**
 * {@code ConfigurationException} indicates that an error has occurred while constructing a configuration instance.
 */
public class ConfigurationException extends RuntimeException {
    private final Set<String> errorDetails;

    public ConfigurationException(String message) {
        this(message, emptySet());
    }

    public ConfigurationException(String message, Set<String> errorDetails) {
        super(message);

        this.errorDetails = errorDetails;
    }

    public ConfigurationException(String message, Throwable cause) {
        this(message, cause, emptySet());
    }

    public ConfigurationException(String message, Throwable cause, Set<String> errorDetails) {
        super(message, cause);

        this.errorDetails = errorDetails;
    }

    public Set<String> getErrorDetails() {
        return errorDetails;
    }
}
