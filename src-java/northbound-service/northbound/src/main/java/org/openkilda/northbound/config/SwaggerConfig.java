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

import org.openkilda.model.SwitchId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Collections;

@Configuration
@EnableSwagger2
public class SwaggerConfig {
    private final ParameterBuilder correlationIdParameter = new ParameterBuilder()
                .name(CORRELATION_ID)
                .description("Request's unique identifier")
                .parameterType("header")
                .modelRef(new ModelRef("string"));

    /**
     * Swagger configuration for API version 1.
     *
     * @return {@link Docket} instance
     */
    @Bean
    public Docket apiV1() {
        return new Docket(DocumentationType.SWAGGER_2)
                .directModelSubstitute(SwitchId.class, String.class)
                .groupName("API v1")
                .apiInfo(new ApiInfoBuilder()
                        .title("Northbound")
                        .description("Kilda SDN Controller API <h1>NOTE: There are features "
                                + "that are present in API v2 but are not present in API v1.</h1>")
                        .version("1.0")
                        .build())
                .globalOperationParameters(Collections.singletonList(correlationIdParameter.build()))
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.openkilda.northbound.controller.v1"))
                .build();
    }

    /**
     * Swagger configuration for API version 2.
     *
     * @return {@link Docket} instance
     */
    @Bean
    public Docket apiV2() {
        return new Docket(DocumentationType.SWAGGER_2)
                .directModelSubstitute(SwitchId.class, String.class)
                .groupName("API v2")
                .apiInfo(new ApiInfoBuilder()
                        .title("Northbound")
                        .description("Kilda SDN Controller API")
                        .version("2.0")
                        .build())
                .globalOperationParameters(Collections.singletonList(correlationIdParameter.required(true).build()))
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.openkilda.northbound.controller.v2"))
                .build();
    }
}
