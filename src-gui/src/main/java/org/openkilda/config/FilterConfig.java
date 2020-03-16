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

package org.openkilda.config;

import org.openkilda.security.filter.LoggingFilter;

import org.springframework.context.annotation.Bean;

import javax.servlet.Filter;

/**
 * The Class FilterConfig.
 */

public class FilterConfig {

    /**
     * Logging filter.
     *
     * @return the filter
     */
    @Bean
    public Filter loggingFilter() {
        return new LoggingFilter();
    }
}
