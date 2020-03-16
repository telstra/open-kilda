/* Copyright 2019 Telstra Open Source
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

package org.openkilda.helper;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.constants.HttpError;
import org.openkilda.constants.IAuthConstants;
import org.openkilda.exception.ExternalSystemException;
import org.openkilda.exception.RestCallFailedException;
import org.openkilda.exception.UnauthorizedException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.model.response.ErrorMessage;
import org.openkilda.service.AuthPropertyService;
import org.openkilda.store.common.model.ApiRequestDto;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.StringUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;

/**
 * The Class RestClientManager.
 */
@Component
public class RestClientManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientManager.class);

    @Autowired
    private AuthPropertyService authPropertyService;

    @Autowired
    private ObjectMapper mapper;
    
    @Autowired
    private ServerContext serverContext;

    /**
     * Invoke.
     *
     * @param apiUrl the api url
     * @param httpMethod the http method
     * @param payload the payload
     * @param contentType the content type
     * @param basicAuth the basic auth
     * @return the http response
     */
    public HttpResponse invoke(final String apiUrl, final HttpMethod httpMethod, final String payload,
            final String contentType, final String basicAuth) {
        HttpResponse httpResponse = null;
        try { 
            RequestContext requestContext = serverContext.getRequestContext();

            HttpClient client = HttpClients.createDefault();
            HttpUriRequest httpUriRequest = null;
            HttpEntityEnclosingRequestBase httpEntityEnclosingRequest = null;
            // Initializing Request
            if (HttpMethod.POST.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPost(apiUrl);
            } else if (HttpMethod.PUT.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPut(apiUrl);
            } else if (HttpMethod.DELETE.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpEntityEnclosingRequestBase() {
                    @Override
                    public String getMethod() {
                        return "DELETE";
                    }
                };
            } else if (HttpMethod.PATCH.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPatch(apiUrl);
            } else {
                httpUriRequest = new HttpGet(apiUrl);
            }

            if (!HttpMethod.POST.equals(httpMethod) && !HttpMethod.PUT.equals(httpMethod) 
                    &&  !HttpMethod.PATCH.equals(httpMethod)  && !HttpMethod.DELETE.equals(httpMethod)) {
                // Setting Required Headers
                if (!StringUtil.isNullOrEmpty(basicAuth)) {
                    LOGGER.debug("[invoke] Setting authorization in header as " + IAuthConstants.Header.AUTHORIZATION);
                    httpUriRequest.setHeader(IAuthConstants.Header.AUTHORIZATION, basicAuth);
                    httpUriRequest.setHeader(IAuthConstants.Header.CORRELATION_ID, requestContext.getCorrelationId());
                }
            }
            if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod) 
                     || HttpMethod.PATCH.equals(httpMethod)) {
                LOGGER.info("[invoke] Executing POST/ PUT request : httpEntityEnclosingRequest : "
                         + httpEntityEnclosingRequest + " : payload : " + payload);
                // Setting POST/PUT related headers
                httpEntityEnclosingRequest.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
                httpEntityEnclosingRequest.setHeader(IAuthConstants.Header.AUTHORIZATION, basicAuth);
                httpEntityEnclosingRequest.setHeader(IAuthConstants.Header.CORRELATION_ID,
                        requestContext.getCorrelationId());
                // Setting request payload
                httpEntityEnclosingRequest.setEntity(new StringEntity(payload));
                httpResponse = client.execute(httpEntityEnclosingRequest);
                LOGGER.debug("[invoke] Call executed successfully");
            } else if (HttpMethod.DELETE.equals(httpMethod)) {
                httpEntityEnclosingRequest.setURI(URI.create(apiUrl));
                LOGGER.info("[invoke] Executing DELETE request : httpDeleteRequest : "
                        + httpEntityEnclosingRequest + " : payload : " + payload);
                // Setting DELETE related headers
                httpEntityEnclosingRequest.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
                httpEntityEnclosingRequest.setHeader(IAuthConstants.Header.EXTRA_AUTH, 
                        String.valueOf(System.currentTimeMillis()));
                httpEntityEnclosingRequest.setHeader(IAuthConstants.Header.AUTHORIZATION, basicAuth);
                httpEntityEnclosingRequest.setHeader(IAuthConstants.Header.CORRELATION_ID,
                        requestContext.getCorrelationId());
                // Setting request payload
                
                httpEntityEnclosingRequest.setEntity(new StringEntity(payload));
                httpResponse = client.execute(httpEntityEnclosingRequest);
                LOGGER.debug("[invoke] Call executed successfully");
            } else {
                LOGGER.info("[invoke] Executing : httpUriRequest : " + httpUriRequest);
                httpResponse = client.execute(httpUriRequest);
                LOGGER.info("[invoke] Call executed successfully");
            }

        } catch (Exception e) {
            LOGGER.error("Error occurred while trying to communicate third party service provider", e);
            throw new RestCallFailedException(e);
        }
        return httpResponse;
    }
    /**
     * Invoke.
     *
     * @param apiRequestDto the api request dto
     * @return the http response
     */
    
    public HttpResponse invoke(final ApiRequestDto apiRequestDto) {
        HttpResponse httpResponse = null;

        String url = apiRequestDto.getUrl();
        String headers = apiRequestDto.getHeader();
        HttpMethod httpMethod = apiRequestDto.getHttpMethod();
        String payload = apiRequestDto.getPayload();

        try {

            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (x509CertChain, authType) -> true)
                    .build();
            CloseableHttpClient client = HttpClientBuilder.create().setSSLContext(sslContext)
                    .setConnectionManager(new PoolingHttpClientConnectionManager(RegistryBuilder
                            .<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.INSTANCE)
                            .register("https",
                                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                            .build()))
                    .build();
            HttpUriRequest httpUriRequest = null;
            HttpEntityEnclosingRequestBase httpEntityEnclosingRequest = null;

            // Initializing Request
            if (HttpMethod.POST.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPost(url);
            } else if (HttpMethod.PUT.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPut(url);
            } else if (HttpMethod.DELETE.equals(httpMethod)) {
                httpUriRequest = new HttpDelete(url);
            } else if (HttpMethod.PATCH.equals(httpMethod)) {
                httpUriRequest = new HttpPatch(url);
            } else {
                httpUriRequest = new HttpGet(url);
            }
            Map<String, String> headersMap = new HashMap<String, String>();
            if (!HttpMethod.POST.equals(httpMethod) && !HttpMethod.PUT.equals(httpMethod)) {
                if (!StringUtil.isNullOrEmpty(headers)) {
                    for (String header : headers.split("\n")) {
                        getHeaders(headersMap, header);
                        for (Entry<String, String> headerEntrySet : headersMap.entrySet()) {
                            httpUriRequest.setHeader(headerEntrySet.getKey(), headerEntrySet.getValue());
                        }
                    }
                }
            }

            if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod)) {
                LOGGER.info("[invoke] Executing POST/ PUT request : httpEntityEnclosingRequest : "
                        + httpEntityEnclosingRequest);
                if (!StringUtil.isNullOrEmpty(headers)) {
                    for (String header : headers.split("\n")) {
                        getHeaders(headersMap, header);
                        for (Entry<String, String> headerEntrySet : headersMap.entrySet()) {
                            httpEntityEnclosingRequest.setHeader(headerEntrySet.getKey(), headerEntrySet.getValue());
                        }
                    }
                }
                // Setting request payload
                httpEntityEnclosingRequest.setEntity(new StringEntity(payload));
                httpResponse = client.execute(httpEntityEnclosingRequest);
                LOGGER.debug("[invoke] Call executed successfully");
            } else {
                LOGGER.info("[invoke] Executing : httpUriRequest : " + httpUriRequest);
                httpResponse = client.execute(httpUriRequest);
                LOGGER.info("[invoke] Call executed successfully");
            }

        } catch (Exception e) {
            LOGGER.error("Error occurred while trying to communicate third party service provider", e);
            throw new RestCallFailedException(e);
        }
        return httpResponse;
    }

    /**
     * Gets the headers.
     *
     * @param headers the header
     * @return the headers
     */
    private Map<String, String> getHeaders(Map<String, String> headersMap, final String headers) {
        String[] headersAry = headers.split(":");
        if (headersAry.length > 1) {
            StringBuilder key = null;
            StringBuilder value = null;
            for (int i = 0; i < headersAry.length; i++) {
                if (i == 0 || i % 2 == 0) {
                    key = new StringBuilder(headersAry[i]);
                } else {
                    value = new StringBuilder(headersAry[i]);
                }
            }
            headersMap.put(key.toString(), value.toString());
        }
        return headersMap;
    }
    
    /**
     * Gets the response list.
     *
     * @param <T> the generic type
     * @param response the response
     * @param responseClass the response class
     * @return the response list
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getResponseList(final HttpResponse response, final Class<T> responseClass) {
        return getResponse(response, List.class, responseClass);
    }

    /**
     * Gets the response.
     *
     * @param <T> the generic type
     * @param response the response
     * @param responseClass the response class
     * @return the response
     */
    public <T> T getResponse(final HttpResponse response, final Class<T> responseClass) {
        return getResponse(response, responseClass, null);
    }

    /**
     * Gets the response.
     *
     * @param <T> the generic type
     * @param <E> the element type
     * @param response the response
     * @param responseClass the response class
     * @param dependentClass the dependent class
     * @return the response
     */
    private <T, E> T getResponse(final HttpResponse response, final Class<T> responseClass,
            final Class<E> dependentClass) {
        T obj = null;
        try {
            LOGGER.info("[getResponse]  : StatusCode " + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() != HttpStatus.NO_CONTENT.value()) {
                String responseEntity = IoUtil.toString(response.getEntity().getContent());

                LOGGER.debug("[getResponse]  : response object " + responseEntity);
                if (!(HttpStatus.valueOf(response.getStatusLine().getStatusCode()).is2xxSuccessful()
                        && response.getEntity() != null)) {
                    String errorMessage = null;
                    try {
                        if (responseEntity.startsWith("[")) {
                            responseEntity = responseEntity.replaceFirst("]", "").trim();
                        }
                        if (responseEntity.endsWith("]")) {
                            responseEntity = responseEntity.replace("]", "").trim();
                        }

                        errorMessage = mapper.readValue(responseEntity, ErrorMessage.class).getMessage();

                    } catch (Exception e) {
                        if (response.getStatusLine().getStatusCode() == HttpStatus.UNAUTHORIZED.value()) {
                            throw new UnauthorizedException(HttpError.UNAUTHORIZED.getMessage());
                        }

                        LOGGER.error("Error occurred while retriving response from third party service provider", e);
                        errorMessage = authPropertyService.getError(IAuthConstants.Code.RESPONSE_PARSING_FAIL_ERROR)
                                .getMessage();
                        throw new RestCallFailedException(errorMessage);
                    }

                    LOGGER.error("Error occurred while retriving response from third party service provider:"
                            + responseEntity);
                    throw new ExternalSystemException(response.getStatusLine().getStatusCode(), errorMessage);

                } else {
                    if (dependentClass == null) {
                        if (responseClass != null) {
                            obj = mapper.readValue(responseEntity, responseClass);
                        }
                    } else {
                        obj = mapper.readValue(responseEntity, TypeFactory.defaultInstance()
                                .constructCollectionLikeType(responseClass, dependentClass));
                    }
                }
            }
        } catch (IOException e) {
            throw new RestCallFailedException(e.getMessage());
        }
        return obj;
    }

    /**
     * Checks if is valid response.
     *
     * @param response the response
     * @return true, if is valid response
     */
    public static boolean isValidResponse(final HttpResponse response) {
        LOGGER.debug("[isValidResponse] Response Code " + response.getStatusLine().getStatusCode());
        boolean isValid = response.getStatusLine().getStatusCode() >= HttpStatus.OK.value()
                && response.getStatusLine().getStatusCode() < HttpStatus.MULTIPLE_CHOICES.value()
                && response.getEntity() != null;
        if (isValid) {
            return true;
        } else {
            try {
                String content = IoUtil.toString(response.getEntity().getContent());
                LOGGER.warn("Found invalid Response. Status Code: " + response.getStatusLine().getStatusCode()
                        + ", content: " + content);
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(), content);
            } catch (IOException exception) {
                LOGGER.warn("Error occurred while vaildating response", exception);
                throw new InvalidResponseException(HttpError.INTERNAL_ERROR.getCode(),
                        HttpError.INTERNAL_ERROR.getMessage());
            }
        }
    }
}
