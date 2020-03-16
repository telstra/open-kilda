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

package org.openkilda.security.filter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The Class LoggingFilter.
 *
 * @author Gaurav Chugh
 */
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);

    private static final String REQUEST_PREFIX = "Request: ";

    private static final String RESPONSE_PREFIX = "Response: ";

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.web.filter.OncePerRequestFilter#doFilterInternal(
     * javax.servlet.http. HttpServletRequest,
     * javax.servlet.http.HttpServletResponse, javax.servlet.FilterChain)
     */
    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (LOGGER.isDebugEnabled()) {
            List<String> apis = Arrays.asList("stats/", "switch/");

            final long startTime = System.currentTimeMillis();
            UUID requestId = UUID.randomUUID();
            request.setAttribute("Id", requestId);
            String fullRequestPath = request.getRequestURL().toString();
            String[] contextVar = request.getRequestURL().toString().split("/");
            String apiName = contextVar[1];
            boolean isMatch = false;

            for (String api : apis) {
                if (apiName.equalsIgnoreCase(api) || fullRequestPath.toLowerCase().contains(api)) {
                    isMatch = true;
                }
            }

            if (isMatch) {
                logRequest(request);
            }
            ResponseWrapper responseWrapper = new ResponseWrapper(requestId, response);
            try {
                filterChain.doFilter(request, responseWrapper);
            } finally {
                try {
                    if (isMatch) {
                        logResponse(responseWrapper);
                    }
                } catch (Exception e) {
                    LOGGER.error("Logging filter. Exception: " + e.getMessage(), e);
                }
            }
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (60000 - elapsedTime < 0) {
                LOGGER.debug("Logging filter delayed request detail - Time Taken: '{}', URL: '{}'", elapsedTime,
                        request.getRequestURL());
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Log request.
     *
     * @param request the request
     */
    private void logRequest(final HttpServletRequest request) {
        StringBuilder msg = new StringBuilder();
        msg.append(REQUEST_PREFIX).append("\n\tid: '").append(request.getAttribute("Id")).append("', ")
                .append("\n\tcontent type: '").append(request.getContentType()).append("', ").append("\n\turl: '")
                .append(request.getRequestURL());
        if (request.getQueryString() != null) {
            msg.append('?').append(request.getQueryString());
        }

        Map<String, String[]> parameters = request.getParameterMap();

        parameters.keySet().forEach((key) -> {
            msg.append("', \n\tparams: '").append(key + " : " + parameters.get(key));
        });

        LOGGER.debug("Log request. Request: " + msg.toString());
    }

    /**
     * Log response.
     *
     * @param response the response
     * @throws JsonParseException the json parse exception
     * @throws JsonMappingException the json mapping exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void logResponse(final ResponseWrapper response)
            throws JsonParseException, JsonMappingException, IOException {
        StringBuilder msg = new StringBuilder();
        msg.append(RESPONSE_PREFIX);
        msg.append("\nid: '").append((response.getId())).append("' ");
        String content = null;
        try {

            ObjectMapper mapper = new ObjectMapper();
            content = new String(response.getData(), response.getCharacterEncoding());
            Object json = mapper.readValue(content, Object.class);

            msg.append("\nResponse: \n").append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Log response. Exception: " + e.getMessage(), e);
        } catch (MismatchedInputException e) {
            msg.append("\nResponse: \n").append(content);
        }
        LOGGER.debug("Log response. Response: " + msg.toString());
    }
}
