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

package org.openkilda.topology.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.error.MessageException;

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
public class TopologyExceptionHandler extends ResponseEntityExceptionHandler {
    /**
     * Handles MessageException exception.
     *
     * @param exception the MessageException instance
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
                status.value(), status.getReasonPhrase(),
                exception.getMessage(), exception.getClass().getSimpleName());
        return super.handleExceptionInternal(exception, error, new HttpHeaders(), status, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        MessageError error = new MessageError(request.getHeader(CORRELATION_ID), System.currentTimeMillis(),
                status.value(), status.getReasonPhrase(),
                exception.getMessage(), exception.getClass().getSimpleName());
        return super.handleExceptionInternal(exception, error, headers, status, request);
    }
}
