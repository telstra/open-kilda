package org.openkilda.helper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.openkilda.exception.ExternalSystemException;
import org.openkilda.exception.RestCallFailedException;
import org.openkilda.exception.UnauthorizedException;
import org.openkilda.model.ErrorMessage;
import org.openkilda.service.impl.AuthPropertyService;
import org.openkilda.utility.HttpError;
import org.openkilda.utility.IAuthConstants;
import org.openkilda.utility.IoUtils;
import org.openkilda.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * The Class RestClientManager.
 */
@Component
public class RestClientManager {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(RestClientManager.class);

    /** The auth property service. */
    @Autowired
    private AuthPropertyService authPropertyService;

    /** The mapper. */
    private ObjectMapper mapper = new ObjectMapper();


    /**
     * Invoke.
     *
     * @param correlationId the correlation id
     * @param apiUrl the api url
     * @param httpMethod the http method
     * @param payload the payload
     * @param token the token
     * @param contentType the content type
     * @param basicAuth the basic auth
     * @return the http response
     */
    public HttpResponse invoke(final String apiUrl, final HttpMethod httpMethod,
            final String payload, final String contentType, final String basicAuth) {
        _log.info("[invoke] - Start");

        HttpResponse httpResponse = null;

        try {
            HttpClient client = HttpClients.createDefault();
            HttpUriRequest httpUriRequest = null;
            HttpEntityEnclosingRequestBase httpEntityEnclosingRequest = null;

            // Initializing Request
            if (HttpMethod.POST.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPost(apiUrl);
            } else if (HttpMethod.PUT.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPut(apiUrl);
            } else if (HttpMethod.DELETE.equals(httpMethod)) {
                httpUriRequest = new HttpDelete(apiUrl);
            } else {
                httpUriRequest = new HttpGet(apiUrl);
            }

            if (!HttpMethod.POST.equals(httpMethod) && !HttpMethod.PUT.equals(httpMethod)) {
                // Setting Required Headers
                if (!StringUtils.isNullOrEmpty(basicAuth)) {
                    _log.debug("[invoke] Setting authorization in header as "
                            + IAuthConstants.Header.AUTHORIZATION);
                    httpUriRequest.setHeader(IAuthConstants.Header.AUTHORIZATION, basicAuth);
                }
            }

            if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod)) {
                _log.info("[invoke] Executing POST/ PUT request : httpEntityEnclosingRequest : "
                        + httpEntityEnclosingRequest + " : payload : " + payload);
                // Setting POST/PUT related headers
                httpEntityEnclosingRequest.setHeader(HttpHeaders.CONTENT_TYPE, contentType);

                httpEntityEnclosingRequest.setHeader(IAuthConstants.Header.BASIC_AUTH, basicAuth);

                // Setting request payload
                httpEntityEnclosingRequest.setEntity(new StringEntity(payload));

                httpResponse = client.execute(httpEntityEnclosingRequest);
                _log.debug("[invoke] Call executed successfully");
            } else if (HttpMethod.DELETE.equals(httpMethod) || HttpMethod.GET.equals(httpMethod)) {
                _log.info("[invoke] Executing DELETE/ GET request : httpUriRequest : "
                        + httpUriRequest);
                httpResponse = client.execute(httpUriRequest);
                _log.info("[invoke] Call executed successfully");
            }

        } catch (Exception e) {
            _log.error("[invoke] Exception: ", e);
            throw new RestCallFailedException(e);
        }
        _log.info("[invoke] - End");
        return httpResponse;
    }

    /**
     * Invoke.
     *
     * @param correlationId the correlation id
     * @param apiUrl the api url
     * @param httpMethod the http method
     * @param payload the payload
     * @param token the token
     * @param contentType the content type
     * @param basicAuth the basic auth
     * @param queryParams the query params
     * @return the http response
     */
    @SuppressWarnings("rawtypes")
    public HttpResponse invoke(final String correlationId, String apiUrl,
            final HttpMethod httpMethod, final String payload, final String token,
            final String contentType, final String basicAuth, final Map<String, String> queryParams) {
        StringBuilder queryStringBuilder = new StringBuilder();
        if (queryParams != null) {
            for (String key : queryParams.keySet()) {

                if (!StringUtils.isNullOrEmpty(key)
                        && !StringUtils.isNullOrEmpty(queryParams.get(key))) {
                    if (queryStringBuilder.length() == 0 && !apiUrl.contains("?")) {
                        queryStringBuilder.append("?");
                    } else {
                        queryStringBuilder.append("&");
                    }
                    queryStringBuilder.append(key).append("=").append(queryParams.get(key));
                }
            }
        }
        return invoke(correlationId, apiUrl + queryStringBuilder.toString(), httpMethod, payload,
                token, contentType, basicAuth, new HashMap());
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
            _log.info("[getResponse]  : StatusCode " + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() != HttpStatus.NO_CONTENT.value()) {
                String responseEntity = IoUtils.getData(response.getEntity().getContent());

                _log.info("[getResponse]  : response object " + responseEntity);
                if (!(HttpStatus.valueOf(response.getStatusLine().getStatusCode())
                        .is2xxSuccessful() && response.getEntity() != null)) {
                    String errorMessage = null;
                    try {
                        if (responseEntity.startsWith("[")) {
                            responseEntity = responseEntity.replaceFirst("]", "").trim();
                        }
                        if (responseEntity.endsWith("]")) {
                            responseEntity = responseEntity.replace("]", "").trim();
                        }

                        errorMessage =
                                mapper.readValue(responseEntity, ErrorMessage.class).getMessage();

                    } catch (Exception e) {
                        if (response.getStatusLine().getStatusCode() == HttpStatus.UNAUTHORIZED
                                .value()) {
                            throw new UnauthorizedException(HttpError.UNAUTHORIZED.getMessage());
                        }

                        _log.error("[getResponse] Exception :", e);
                        errorMessage =
                                authPropertyService.getError(
                                        IAuthConstants.Code.RESPONSE_PARSING_FAIL_ERROR)
                                        .getMessage();
                        throw new RestCallFailedException(errorMessage);
                    }

                    _log.error("[getResponse] Exception : " + responseEntity);
                    throw new ExternalSystemException(response.getStatusLine().getStatusCode(),
                            errorMessage);

                } else {
                    if (dependentClass == null) {
                        obj = mapper.readValue(responseEntity, responseClass);
                    } else {
                        obj =
                                mapper.readValue(responseEntity, TypeFactory.defaultInstance()
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
        _log.debug("[isValidResponse] Response Code " + response.getStatusLine().getStatusCode());
        return response.getStatusLine().getStatusCode() >= HttpStatus.OK.value()
                && response.getStatusLine().getStatusCode() < HttpStatus.MULTIPLE_CHOICES.value()
                && response.getEntity() != null;
    }

}
