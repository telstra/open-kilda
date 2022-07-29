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

package org.openkilda.northbound.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.northbound.utils.RequestCorrelationId.RequestCorrelationClosable;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Spring Web filter which initializes the correlation context with either provided "correlation_id" (HTTP header) or a
 * new one.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private static final List<String> EXCLUDE_PATTERNS = ImmutableList.of(
            "/",
            "/v1/health-check",
            // swagger related patterns
            "/swagger*/**",
            "/v3/api-docs/**"
    );

    private final PathMatcher matcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDE_PATTERNS.stream()
                .anyMatch(p -> matcher.match(p, request.getServletPath()));
    }

    /**
     * Generates new correlation_id and add it into passing correlation id in the header.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID);

        boolean emptyCorrelationId = StringUtils.isBlank(correlationId);
        if (emptyCorrelationId) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = RequestCorrelationId.chain(UUID.randomUUID().toString(), correlationId);
        }

        try (RequestCorrelationClosable requestCorrelation = RequestCorrelationId.create(correlationId)) {
            // Put the request's correlationId into the logger context.
            // MDC is picked up by the %X in log4j2 formatter .. resources/log4j2.xml
            try (MDCCloseable closable = MDC.putCloseable(CORRELATION_ID, correlationId)) {
                if (emptyCorrelationId) {
                    logger.warn("CorrelationId was not sent, generated one: {}", correlationId);
                } else {
                    logger.trace("Found correlationId in header. Chaining: {}", correlationId);
                }

                response.addHeader(CORRELATION_ID, correlationId);
                filterChain.doFilter(request, response);
            }
        }
    }
}
