package org.bitbucket.openkilda.northbound.utils;

import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;

import org.bitbucket.openkilda.northbound.model.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Common exception handler for controllers.
 */
@ControllerAdvice
public class NorthboundExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(NorthboundExceptionHandler.class);

    /**
     * Handles NorthboundException exception.
     *
     * @param exception the NorthboundException instance
     * @param request   the WebRequest caused exception
     * @return the ResponseEntity object instance
     */
    @ExceptionHandler(NorthboundException.class)
    protected ResponseEntity<Object> handleNorthboundException(NorthboundException exception, WebRequest request) {
        HttpStatus status;

        switch (exception.getErrorType()) {
            case ENTITY_NOT_FOUND:
                status = HttpStatus.NOT_FOUND;
                break;
            case ENTITY_ALREADY_EXISTS:
                status = HttpStatus.CONFLICT;
                break;
            case AUTH_FAILED:
            case TOKEN_EXPIRED:
                status = HttpStatus.UNAUTHORIZED;
                break;
            case ENTITY_INVALID:
            case REQUEST_INVALID:
            default:
                status = HttpStatus.BAD_REQUEST;
                break;
        }

        ErrorResponse error = new ErrorResponse(status, exception.getMessage(), exception.getClass().getSimpleName(),
                request.getHeader(CORRELATION_ID), System.nanoTime());
        return handleExceptionInternal(exception, error, new HttpHeaders(), status, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        ErrorResponse error = new ErrorResponse(status, exception.getMessage(), exception.getClass().getSimpleName(),
                request.getHeader(CORRELATION_ID), System.nanoTime());
        return super.handleExceptionInternal(exception, error, headers, status, request);
    }
}
