package org.openkilda.northbound.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(RequestCorrelation.CORRELATION_ID);
        if (StringUtils.isBlank(correlationId)) {
            correlationId = UUID.randomUUID().toString();
            LOGGER.debug("CorrelationId was not sent, generated one: {}", correlationId);
        } else {
            LOGGER.debug("Found correlationId in header: {}", correlationId);
        }
        RequestCorrelation.setId(correlationId);

        // Put the request's correlationId into the logger context.
        // MDC is picked up by the %X in log4j2 formatter .. resources/log4j2.xml
        try(MDCCloseable closable = MDC.putCloseable(CORRELATION_ID, correlationId)) {
            filterChain.doFilter(request, response);
        }
    }

}
