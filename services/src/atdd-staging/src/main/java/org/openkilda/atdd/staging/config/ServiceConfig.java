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

package org.openkilda.atdd.staging.config;

import com.google.common.io.CharStreams;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile("default")
@PropertySource("file:${kilda.config.file}")
@ComponentScan(basePackages = {"org.openkilda.atdd.staging.service"})
public class ServiceConfig {

    @Bean(name = "northboundRestTemplate")
    public RestTemplate northboundRestTemplate(
            @Value("${northbound.endpoint}") String endpoint,
            @Value("${northbound.username}") String username,
            @Value("${northbound.password}") String password) {
        return buildRestTemplateWithAuth(endpoint, username, password);
    }

    @Bean(name = "floodlightRestTemplate")
    public RestTemplate floodlightRestTemplate(
            @Value("${floodlight.endpoint}") String endpoint,
            @Value("${floodlight.username}") String username,
            @Value("${floodlight.password}") String password) {
        return buildRestTemplateWithAuth(endpoint, username, password);
    }

    @Bean(name = "topologyEngineRestTemplate")
    public RestTemplate topologyEngineRestTemplate(
            @Value("${topology-engine-rest.endpoint}") String endpoint,
            @Value("${topology-engine-rest.username}") String username,
            @Value("${topology-engine-rest.password}") String password) {
        return buildRestTemplateWithAuth(endpoint, username, password);
    }

    // The retrier is used for repeating operations which depend on the system state and may change the result after delays.
    @Bean(name = "topologyEngineRetryPolicy")
    public RetryPolicy retryPolicy() {
        return new RetryPolicy()
                .withDelay(2, TimeUnit.SECONDS)
                .withMaxRetries(10);
    }

    @Bean(name = "traffExamRestTemplate")
    public RestTemplate traffExamRestTemplate() {
        return new RestTemplate();
    }

    private RestTemplate buildRestTemplateWithAuth(String endpoint, String username, String password) {
        final RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(endpoint));
        restTemplate.getInterceptors().add(new BasicAuthorizationInterceptor(username, password));
        restTemplate.setErrorHandler(buildErrorHandler());
        return restTemplate;
    }

    private ResponseErrorHandler buildErrorHandler() {
        return new DefaultResponseErrorHandler() {
            private final Logger LOGGER = LoggerFactory.getLogger(ResponseErrorHandler.class);

            @Override
            public void handleError(ClientHttpResponse clientHttpResponse) throws IOException {
                try {
                    super.handleError(clientHttpResponse);
                } catch(RestClientResponseException e) {
                    if (e.getRawStatusCode() != HttpStatus.NOT_FOUND.value()) {
                        LOGGER.error("HTTP response with status {} and body '{}'", e.getRawStatusCode(),
                                e.getResponseBodyAsString());
                    }
                    throw e;
                }
            }
        };
    }
}
