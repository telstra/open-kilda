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

package org.openkilda.utility;

import lombok.Getter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ApplicationProperties: is used to read properties from external file. Externalized Configuration
 * is being done with the reference of: https://docs.spring
 * .io/spring-boot/docs/current/reference/html/boot-features-external -config.html
 *
 * @author Gaurav Chugh
 *
 */
@Component
@Getter
public class ApplicationProperties {

    @Value("${nb.base.url}")
    private String nbBaseUrl;

    @Value("${opentsdb.base.url}")
    private String openTsdbBaseUrl;

    @Value("${opentsdb.metric.prefix}")
    private String openTsdbMetricPrefix;

    @Value("${kilda.username}")
    private String kildaUsername;

    @Value("${kilda.password}")
    private String kildaPassword;

    @Value("${switch.data.file.path}")
    private String switchDataFilePath;
    
}
