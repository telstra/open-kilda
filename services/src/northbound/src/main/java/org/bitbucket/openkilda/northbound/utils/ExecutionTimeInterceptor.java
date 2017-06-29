package org.bitbucket.openkilda.northbound.utils;

import static org.bitbucket.openkilda.messaging.Utils.TIMESTAMP;

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
