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

package org.openkilda.integration.service;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.config.ApplicationProperties;
import org.openkilda.constants.IAuthConstants;
import org.openkilda.constants.IConstants;
import org.openkilda.model.victoria.RangeQueryParams;
import org.openkilda.model.victoria.Status;
import org.openkilda.model.victoria.dbdto.VictoriaDbRes;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class StatsIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsIntegrationService.class);

    private final ApplicationProperties appProps;
    private final ServerContext serverContext;
    private final RestTemplate restTemplate;


    public StatsIntegrationService(ApplicationProperties appProps,
                                   ServerContext serverContext,
                                   RestTemplate restTemplate) {
        this.appProps = appProps;
        this.serverContext = serverContext;
        this.restTemplate = restTemplate;
    }

    /**
     * Retrieves Victoria Metrics statistics for the specified range query parameters.
     *
     * @param rangeQueryParamsRequest The RangeQueryParams containing query parameters for the Victoria Metrics request.
     * @return A VictoriaResponse object containing the response from the Victoria Metrics server.
     */
    public VictoriaDbRes getVictoriaStats(RangeQueryParams rangeQueryParamsRequest) {
        LOGGER.info("Getting victoria stats for the following requestParams: {}", rangeQueryParamsRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        RequestContext requestContext = serverContext.getRequestContext();
        headers.set(IAuthConstants.Header.CORRELATION_ID, requestContext.getCorrelationId());

        HttpEntity<MultiValueMap<String, Object>> requestEntity
                = getMultiValueMapHttpEntity(rangeQueryParamsRequest, headers);
        String url = appProps.getVictoriaBaseUrl() + IConstants.VictoriaMetricsUrl.VICTORIA_RANGE_QUERY;
        try {
            LOGGER.info("Request to Victoria DB with the following url: {}", url);
            ResponseEntity<VictoriaDbRes> responseEntity
                    = restTemplate.postForEntity(url, requestEntity, VictoriaDbRes.class);
            LOGGER.info("Received response from victoriaDb with the following http code: {}, status: {}, error: {}",
                    responseEntity.getStatusCodeValue(),
                    responseEntity.getBody().getStatus(),
                    responseEntity.getBody().getError());
            return responseEntity.getBody();
        } catch (ResourceAccessException e) {
            LOGGER.error("Error while accessing VictoriaDB with the following URL: {}", url, e);
            return VictoriaDbRes.builder().status(Status.ERROR).errorType("500")
                    .error("Can not access stats at the moment, something wrong with the Victoria DB").build();
        }
    }

    private static HttpEntity<MultiValueMap<String, Object>> getMultiValueMapHttpEntity(
            RangeQueryParams rangeQueryParamsRequest, HttpHeaders headers) {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("query", rangeQueryParamsRequest.getQuery());
        formData.add("start", rangeQueryParamsRequest.getStart());
        if (rangeQueryParamsRequest.getEnd() != null) {
            formData.add("end", rangeQueryParamsRequest.getEnd());
        }
        if (StringUtils.isNotBlank(rangeQueryParamsRequest.getStep())) {
            formData.add("step", rangeQueryParamsRequest.getStep());
        }
        // Create HttpEntity with form data and headers
        return new HttpEntity<>(formData, headers);
    }
}
