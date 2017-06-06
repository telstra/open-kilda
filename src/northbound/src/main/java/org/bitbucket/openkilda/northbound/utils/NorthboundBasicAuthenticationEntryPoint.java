package org.bitbucket.openkilda.northbound.utils;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.error.MessageError;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
     * Instance constructor.
     * Sets the default basic realm value
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
        MessageError error = new MessageError(request.getHeader(CORRELATION_ID), System.currentTimeMillis(),
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorType.AUTH_FAILED.toString(), exception.getClass().getSimpleName());
        response.getWriter().print(MAPPER.writeValueAsString(error));
    }
}
