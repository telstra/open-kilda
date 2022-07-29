/* Copyright 2022 Telstra Open Source
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

import org.openkilda.model.SwitchId;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    /**
     * Swagger configuration for API version 1.
     */
    @Bean
    public GroupedOpenApi apiV1() {
        SpringDocUtils.getConfig()
                .replaceWithClass(SwitchId.class, String.class);

        return GroupedOpenApi.builder()
                .group("Northbound-API-v1")
                .packagesToScan("org.openkilda.northbound.controller.v1")
                .addOpenApiCustomiser(openApi -> {
                    openApi.getInfo().title("Northbound")
                            .description("Kilda SDN Controller API")
                            .version("1.0");

                    openApi.getComponents()
                            .addSecuritySchemes("basicAuth", new SecurityScheme().type(Type.HTTP).scheme("basic"));
                    openApi.addSecurityItem(new SecurityRequirement().addList("basicAuth"));

                    openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream())
                            .forEach(operation -> operation.addParametersItem(
                                    new HeaderParameter()
                                            .name(CORRELATION_ID)
                                            .description("Request's unique identifier")
                                            .schema(new StringSchema())));
                })
                .build();
    }

    /**
     * Swagger configuration for API version 2.
     */
    @Bean
    public GroupedOpenApi apiV2() {
        SpringDocUtils.getConfig()
                .replaceWithClass(SwitchId.class, String.class);

        return GroupedOpenApi.builder()
                .group("Northbound-API-v2")
                .packagesToScan("org.openkilda.northbound.controller.v2")
                .addOpenApiCustomiser(openApi -> {
                    openApi.getInfo().title("Northbound")
                            .description("Kilda SDN Controller API")
                            .version("2.0");

                    openApi.getComponents()
                            .addSecuritySchemes("basicAuth", new SecurityScheme().type(Type.HTTP).scheme("basic"));
                    openApi.addSecurityItem(new SecurityRequirement().addList("basicAuth"));

                    openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream())
                            .forEach(operation ->
                                    operation.addParametersItem(
                                            new HeaderParameter()
                                                    .name(CORRELATION_ID)
                                                    .description("Request's unique identifier")
                                                    .required(true)
                                                    .schema(new StringSchema())));
                })
                .build();
    }
}
