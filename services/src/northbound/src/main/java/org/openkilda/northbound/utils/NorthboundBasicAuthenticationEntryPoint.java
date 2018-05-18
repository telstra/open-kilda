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

package org.openkilda.northbound.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Basic auth entry point representation class.
 */
@Component
public class NorthboundBasicAuthenticationEntryPoint extends BasicAuthenticationEntryPoint {
    /**
     * Basic realm value.
     */
    private static final String DEFAULT_REALM = "Kilda";

    /**
     * Instance constructor. Sets the default basic realm value
     */
    public NorthboundBasicAuthenticationEntryPoint() {
        setRealmName(DEFAULT_REALM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String realm = String.format("Basic realm=%s", getRealmName());
        response.addHeader(HttpHeaders.WWW_AUTHENTICATE, realm);
        response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_ID)).orElse(DEFAULT_CORRELATION_ID);
        MessageError error = new MessageError(correlationId, System.currentTimeMillis(),
                ErrorType.AUTH_FAILED.toString(), DEFAULT_REALM, exception.getClass().getSimpleName());
        response.getWriter().print(MAPPER.writeValueAsString(error));
    }
}
