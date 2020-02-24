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

import static org.openkilda.messaging.Utils.EXTRA_AUTH;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Interceptor which enforces extra authentication for methods annotated with {@link ExtraAuthRequired}.
 */
public class ExtraAuthInterceptor extends HandlerInterceptorAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtraAuthInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!supports(handler)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        ExtraAuthRequired annotation = handlerMethod.getMethodAnnotation(ExtraAuthRequired.class);
        if (annotation == null) {
            Class<?> handlerClass = handlerMethod.getMethod().getDeclaringClass();
            annotation = AnnotationUtils.findAnnotation(handlerClass, ExtraAuthRequired.class);
            if (annotation == null) {
                return true;
            }
        }

        long currentAuth = System.currentTimeMillis();

        final String extraAuthHeader = request.getHeader(EXTRA_AUTH);
        long extraAuth;
        try {
            extraAuth = Long.parseLong(extraAuthHeader);
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid {} header: {}", EXTRA_AUTH, extraAuthHeader);

            response.getWriter().write("Invalid Auth: " + currentAuth);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        if (Math.abs(currentAuth - extraAuth) > TimeUnit.SECONDS.toMillis(120)) {
            /*
             * The request needs to be within 120 seconds of the system clock.
             */
            response.getWriter().write("Invalid Auth: " + currentAuth);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        return true;
    }

    private boolean supports(Object handler) {
        return handler instanceof HandlerMethod;
    }
}
