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

import java.net.SocketException;
import java.util.concurrent.CompletionException;

@ControllerAdvice
public class GrpcExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GrpcRequestFailureException.class)
    protected ResponseEntity<Object> handleGrpcRequestExeption(GrpcRequestFailureException ex,
                                                               WebRequest request) {
        HttpStatus status;

        switch (ex.getErrorType()) {
            case AUTH_FAILED:
                status = HttpStatus.UNAUTHORIZED;
                break;
            case NOT_FOUND:
                status = HttpStatus.NOT_FOUND;
                break;
            case ALREADY_EXISTS:
                status = HttpStatus.CONFLICT;
                break;
            default:
                status = HttpStatus.BAD_REQUEST;
                break;
        }

        return makeExceptionalResponse(ex, makeErrorPayload(ex.getCode(), ex.getMessage()), status, request);
    }

    @ExceptionHandler(CompletionException.class)
    protected ResponseEntity<Object> handleCompletionExceptions(CompletionException wrapper, WebRequest request) {
        ResponseEntity<Object> response;
        try {
            throw wrapper.getCause();
        } catch (SocketException e) {
            GrpcMessageError body = makeErrorPayload(-1, format("Communication failure - %s", e.getMessage()));
            response = makeExceptionalResponse(e, body, HttpStatus.INTERNAL_SERVER_ERROR, request);
        } catch (Exception e) {
            GrpcMessageError body = makeErrorPayload(-1, ErrorType.INTERNAL_ERROR.toString());
            response = makeExceptionalResponse(
                    e, body, HttpStatus.INTERNAL_SERVER_ERROR, request);
        } catch (Throwable e) {
            GrpcMessageError body = makeErrorPayload(-1, ErrorType.INTERNAL_ERROR.toString());
            response = new ResponseEntity<>(body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        GrpcMessageError error = new GrpcMessageError(System.currentTimeMillis(), -1L, exception.getMessage(),
                ErrorType.REQUEST_INVALID.toString());
        return makeExceptionalResponse(exception, error, status, request);
    }

    private ResponseEntity<Object> makeExceptionalResponse(
            Exception ex, GrpcMessageError body, HttpStatus status, WebRequest request) {
        logger.error(format("Produce error response: %s", body), ex);
        return super.handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }

    private GrpcMessageError makeErrorPayload(Integer code, String message) {
        return new GrpcMessageError(System.currentTimeMillis(), code, message);
    }
}
