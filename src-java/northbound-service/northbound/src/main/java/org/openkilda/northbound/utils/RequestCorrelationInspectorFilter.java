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

package org.openkilda.northbound.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import javax.annotation.Priority;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Filter that controls existence of correlation id in the header. It should not allow to access to any resources
 * without correlation id (except v1 APIs and swagger API).
 */
@Priority(value = Ordered.LOWEST_PRECEDENCE - 1)
@Component
public class RequestCorrelationInspectorFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDE_PATTERNS = ImmutableList.of(
            "/v1/**",
            // swagger related patterns
            "/swagger*/**",
            "/webjars/**",
            "/v2/api-docs"
    );

    private final PathMatcher matcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDE_PATTERNS.stream()
                .anyMatch(p -> matcher.match(p, request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID);
        if (StringUtils.isBlank(correlationId)) {
            response.getWriter().write("A request header 'correlation_id' with specified value is required");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
