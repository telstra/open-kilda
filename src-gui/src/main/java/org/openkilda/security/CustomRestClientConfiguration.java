/* Copyright 2023 Telstra Open Source
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

package org.openkilda.security;

import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;


@Configuration
public class CustomRestClientConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomRestClientConfiguration.class);

    @Value("${victoria.trust.store}")
    private Resource victoriaTrustStore;

    @Value("${victoria.trust.store.password}")
    private String trustStorePassword;

    @Bean
    public RestTemplate restTemplate() {
        try {
            LOGGER.info("Configuring restTemplate with the trustStore:{}", victoriaTrustStore.getURL());

            if (!victoriaTrustStore.exists() || !victoriaTrustStore.isReadable()) {
                LOGGER.error("Is resource exist? {}. Is resource readable? {}",
                        victoriaTrustStore.exists(), victoriaTrustStore.isReadable());
                throw new ResourceAccessException("CA certificate for victoriaDb has not been provided");
            }
            LOGGER.info("Resource content length: {}", victoriaTrustStore.contentLength());
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(victoriaTrustStore.getURL(),
                            StringUtils.isBlank(trustStorePassword) ? null : trustStorePassword.toCharArray()).build();
            LOGGER.debug("sslContext has been initialized");
            SSLConnectionSocketFactory sslConFactory = new SSLConnectionSocketFactory(sslContext);
            LOGGER.debug("sslConFactory has been initialized");
            CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslConFactory).build();
            LOGGER.debug("httpClient has been initialized");
            ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
            LOGGER.debug("requestFactory has been initialized");
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            LOGGER.info("restTemplate has been initialized");
            return restTemplate;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize RestTemplate with SSL context. Using a simple RestTemplate.", e);
            return new RestTemplate();
        }

    }
}
