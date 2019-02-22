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

package org.openkilda.grpc.speaker.utils;

import static java.lang.String.format;

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.GrpcMessageError;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GrpcExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GrpcRequestFailureException.class)
    protected ResponseEntity<Object> handleGrpcRequestExeption(GrpcRequestFailureException ex,
                                                               WebRequest request) {
        HttpStatus status;

        switch (ex.getCode()) {
            case 57:
                status = HttpStatus.UNAUTHORIZED;
                break;
            case 191:
                status = HttpStatus.NOT_FOUND;
                break;
            default:
                status = HttpStatus.BAD_REQUEST;
                break;
        }

        GrpcMessageError error = new GrpcMessageError(System.currentTimeMillis(), ex.getCode(), ex.getMessage());
        logger.warn(format("Error %s caught.", error), ex);

        return super.handleExceptionInternal(ex, error, new HttpHeaders(), status, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        GrpcMessageError error = new GrpcMessageError(System.currentTimeMillis(), -1L, exception.getMessage(),
                ErrorType.REQUEST_INVALID.toString());

        logger.error(format("Unknown error %s caught.", error), exception);

        return super.handleExceptionInternal(exception, error, headers, status, request);
    }
}
