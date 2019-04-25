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

import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The interceptor for request processing time calculation.
 */
public class ExecutionTimeInterceptor implements HandlerInterceptor {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeInterceptor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object obj) {
        request.setAttribute(TIMESTAMP, System.currentTimeMillis());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object obj, ModelAndView mv) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object obj, Exception ex) {
        long executed = System.currentTimeMillis() - (long) request.getAttribute(TIMESTAMP);
        logger.debug("execution-time ms: {}", executed);
    }
}
