package org.bitbucket.openkilda.northbound.utils;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;

import org.bitbucket.openkilda.messaging.error.MessageError;
import org.bitbucket.openkilda.messaging.error.MessageException;

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
    /**
     * Handles NorthboundException exception.
     *
     * @param exception the NorthboundException instance
     * @param request   the WebRequest caused exception
     * @return the ResponseEntity object instance
     */
    @ExceptionHandler(MessageException.class)
    protected ResponseEntity<Object> handleMessageException(MessageException exception, WebRequest request) {
        HttpStatus status;

        switch (exception.getErrorType()) {
            case NOT_FOUND:
                status = HttpStatus.NOT_FOUND;
                break;
            case DATA_INVALID:
            case PARAMETERS_INVALID:
                status = HttpStatus.BAD_REQUEST;
                break;
            case ALREADY_EXISTS:
                status = HttpStatus.CONFLICT;
                break;
            case AUTH_FAILED:
                status = HttpStatus.UNAUTHORIZED;
                break;
            case OPERATION_TIMED_OUT:
            case INTERNAL_ERROR:
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        MessageError error = new MessageError(request.getHeader(CORRELATION_ID), exception.getTimestamp(),
                status.value(), status.getReasonPhrase(), exception.getMessage(), exception.getClass().getSimpleName());
        return super.handleExceptionInternal(exception, error, new HttpHeaders(), status, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        MessageError error = new MessageError(request.getHeader(CORRELATION_ID), System.currentTimeMillis(),
                status.value(), status.getReasonPhrase(), exception.getMessage(), exception.getClass().getSimpleName());
        return super.handleExceptionInternal(exception, error, headers, status, request);
    }
}
