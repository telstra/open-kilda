/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.config;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean(name = "apiV1")
    public GroupedOpenApi apiV1() {
        return GroupedOpenApi.builder()
                .group("api_v1")
                .pathsToMatch("/v1/**")
                .displayName("OpenKilda Northbound API v1")
                .packagesToScan("org.openkilda.northbound.controller.v1")
                .addOperationCustomizer(addCorrelationIdHeader())
                .build();
    }

    @Bean
    public GroupedOpenApi apiV2() {
        return GroupedOpenApi.builder()
                .group("api_v2")
                .pathsToMatch("/v2/**")
                .packagesToScan("org.openkilda.northbound.controller.v2")
                .displayName("OpenKilda Northbound API v2")
                .addOperationCustomizer(addCorrelationIdHeader())
                .build();
    }

    private OperationCustomizer addCorrelationIdHeader() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new HeaderParameter()
                            .name(CORRELATION_ID)
                            .description("Request's unique identifier.")
                            .required(true)
            );
            return operation;
        };
    }
}
