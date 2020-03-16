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

package org.openkilda.integration.auth.oauth.service;

import org.openkilda.exception.AuthenticationException;
import org.openkilda.exception.RestCallFailedException;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.auth.oauth.dto.Token;
import org.openkilda.integration.auth.service.IAuthService;
import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.common.helper.PrepareRequest;
import org.openkilda.store.common.model.ApiRequestDto;
import org.openkilda.store.model.AuthConfigDto;
import org.openkilda.store.model.OauthTwoConfigDto;
import org.openkilda.store.model.UrlDto;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OAuthService extends IAuthService {

    @Autowired
    private RestClientManager restClientManager;
    
    @Autowired
    private PrepareRequest prepareRequest;

    public AuthType getAuthType() {
        return AuthType.OAUTH_TWO;
    }

    @Override
    public <T> T getResponse(UrlDto request, AuthConfigDto authDto, Class<T> responseClass) {
        T obj = null;
        try {
            HttpResponse response = getHttpResponse(request, authDto);
            obj = restClientManager.getResponse(response, responseClass);
        } catch (RestCallFailedException e) {
            e.printStackTrace();
        }
        return obj;
    }

    @Override
    public <T> List<T> getResponseList(UrlDto request, AuthConfigDto authDto, Class<T> responseClass) {
        List<T> obj = null;
        try {
            HttpResponse response = getHttpResponse(request, authDto);
            obj = restClientManager.getResponseList(response, responseClass);
        } catch (RestCallFailedException e) {
            e.printStackTrace();
        }
        return obj;
    }

    private HttpResponse getHttpResponse(UrlDto request, AuthConfigDto authDto) {
        try {
            String accessToken = getToken((OauthTwoConfigDto) authDto);

            if (request.getHeader() != null) {
                request.setHeader(request.getHeader() + "\nAuthorization:" + accessToken);
            } else {
                request.setHeader("Authorization:" + accessToken);
            }
            HttpMethod httpMethod = null;
            if (("POST").equalsIgnoreCase(request.getMethodType())) {
                httpMethod = HttpMethod.POST;
            } else if (("PUT").equalsIgnoreCase(request.getMethodType())) {
                httpMethod = HttpMethod.PUT;
            } else if (("DELETE").equalsIgnoreCase(request.getMethodType())) {
                httpMethod = HttpMethod.DELETE;
            } else {
                httpMethod = HttpMethod.GET;
            }
            ApiRequestDto apiRequestDto = new ApiRequestDto(request.getUrl(), httpMethod, request.getHeader(),
                    request.getBody());

            if (request.getParams() != null) {
                prepareRequest.preprocessApiRequest(apiRequestDto, request.getParams());
            }

            return restClientManager.invoke(apiRequestDto);
        } catch (RestCallFailedException | AuthenticationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getToken(OauthTwoConfigDto oauthTwoConfigDto)
            throws RestCallFailedException, AuthenticationException {

        Token token = generateToken("", oauthTwoConfigDto.getOauthGenerateTokenUrl().getUrl(),
                oauthTwoConfigDto.getUsername(), oauthTwoConfigDto.getPassword());
        if (token.getExpiresIn() < 500) {
            token = refreshToken(oauthTwoConfigDto.getOauthRefreshTokenUrl().getUrl(), token.getRefreshToken());
        }
        return (token.getTokenType().substring(0, 1).toUpperCase() + token.getTokenType().substring(1)) + " "
                + token.getAccessToken();
    }

    private Token generateToken(final String correlationId, final String url, final String userName,
            final String password) throws AuthenticationException, RestCallFailedException {
        String headers = HttpHeaders.CONTENT_TYPE + ":application/x-www-form-urlencoded";
        String payload = "grant_type=password&username=" + userName + "&password=" + password;

        ApiRequestDto apiRequestDto = new ApiRequestDto(url, HttpMethod.POST, headers, payload);

        HttpResponse response = restClientManager.invoke(apiRequestDto);

        return restClientManager.getResponse(response, Token.class);
    }

    private Token refreshToken(final String url, final String refreshToken)
            throws AuthenticationException, RestCallFailedException {
        String headers = HttpHeaders.CONTENT_TYPE + ":application/x-www-form-urlencoded";
        String payload = "grant_type=refresh_token&refresh_token=" + refreshToken;

        ApiRequestDto apiRequestDto = new ApiRequestDto(url, HttpMethod.POST, headers, payload);

        HttpResponse response = restClientManager.invoke(apiRequestDto);

        return restClientManager.getResponse(response, Token.class);
    }
}
