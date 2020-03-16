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

package org.openkilda.security.mapper;

import static java.util.stream.Collectors.joining;

import org.openkilda.constants.HttpError;
import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.TwoFaKeyNotSetException;
import org.openkilda.model.response.ErrorMessage;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.usermanagement.exception.RequestValidationException;

import java.nio.file.AccessDeniedException;

/**
 * The Class GlobalExceptionMapper.
 */
public class GlobalExceptionMapper extends ResponseEntityExceptionHandler {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(GlobalExceptionMapper.class);

    /**
     * Instantiates a new global exception mapper.
     */
    public GlobalExceptionMapper() {
        _log.info("Global exception mapper. Initializing {}...", GlobalExceptionMapper.class.getName());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            final HttpRequestMethodNotSupportedException ex, final HttpHeaders headers, final HttpStatus status,
            final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.METHOD_NOT_ALLOWED.getHttpStatus(), HttpError.METHOD_NOT_ALLOWED.getCode(),
                HttpError.METHOD_NOT_ALLOWED.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(final HttpMediaTypeNotSupportedException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.UNPROCESSABLE_ENTITY.getHttpStatus(), HttpError.UNPROCESSABLE_ENTITY.getCode(),
                HttpError.UNPROCESSABLE_ENTITY.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(final HttpMediaTypeNotAcceptableException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.UNPROCESSABLE_ENTITY.getHttpStatus(), HttpError.UNPROCESSABLE_ENTITY.getCode(),
                HttpError.UNPROCESSABLE_ENTITY.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(final MissingPathVariableException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            final MissingServletRequestParameterException ex, final HttpHeaders headers, final HttpStatus status,
            final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(final ServletRequestBindingException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.METHOD_NOT_FOUND.getHttpStatus(), HttpError.METHOD_NOT_FOUND.getCode(),
                HttpError.METHOD_NOT_FOUND.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleConversionNotSupported(final ConversionNotSupportedException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.UNPROCESSABLE_ENTITY.getHttpStatus(), HttpError.UNPROCESSABLE_ENTITY.getCode(),
                HttpError.UNPROCESSABLE_ENTITY.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(final TypeMismatchException ex, final HttpHeaders headers,
            final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.METHOD_NOT_ALLOWED.getHttpStatus(), HttpError.METHOD_NOT_ALLOWED.getCode(),
                HttpError.METHOD_NOT_ALLOWED.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(final HttpMessageNotReadableException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        String message = new String();
        if (ex.getCause() instanceof JsonMappingException) {
            StringBuilder msg = new StringBuilder();
            JsonMappingException jme = (JsonMappingException) ex.getCause();
            for (Reference reference : jme.getPath()) {
                msg.append(reference.getFieldName()).append(" -> ");
            }
            if (msg.length() > 0) {
                msg.setLength(msg.length() - 3);
                msg.append(": INVALID.");
            }
            message = msg.toString();
        } else {
            message = ex.getMessage();
        }
        return response(HttpError.UNPROCESSABLE_ENTITY.getHttpStatus(), HttpError.UNPROCESSABLE_ENTITY.getCode(),
                HttpError.UNPROCESSABLE_ENTITY.getAuxilaryMessage(), message);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotWritable(final HttpMessageNotWritableException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.UNPROCESSABLE_ENTITY.getHttpStatus(), HttpError.UNPROCESSABLE_ENTITY.getCode(),
                HttpError.UNPROCESSABLE_ENTITY.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(final MethodArgumentNotValidException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        String message = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                .collect(joining(", "));
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), message);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(final MissingServletRequestPartException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), ex.toString());
    }

    @Override
    protected ResponseEntity<Object> handleBindException(final BindException ex, final HttpHeaders headers,
            final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        String message = ex.getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(joining(", "));
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), message);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(final NoHandlerFoundException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.METHOD_NOT_FOUND.getHttpStatus(), HttpError.METHOD_NOT_FOUND.getCode(),
                HttpError.METHOD_NOT_FOUND.getAuxilaryMessage(), ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestTimeoutException(final AsyncRequestTimeoutException ex,
            final HttpHeaders headers, final HttpStatus status, final WebRequest webRequest) {
        return super.handleAsyncRequestTimeoutException(ex, headers, status, webRequest);
    }

    /**
     * Response.
     *
     * @param status the status
     * @param code the code
     * @param auxilaryMessage the auxilary message
     * @param message the message
     * @return the response entity
     */
    protected ResponseEntity<Object> response(final HttpStatus status, final Integer code, final String auxilaryMessage,
            final String message) {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new ResponseEntity<Object>(new ErrorMessage(code, message, auxilaryMessage), headers, status);
    }

    /**
     * Response.
     *
     * @param status the status
     * @param code the code
     * @param auxilaryMessage the auxilary message
     * @param message the message
     * @param correlationId the correlation id
     * @return the response entity
     */
    protected ResponseEntity<Object> response(final HttpStatus status, final Integer code, final String auxilaryMessage,
            final String message, final String correlationId) {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new ResponseEntity<Object>(new ErrorMessage(code, message, auxilaryMessage, correlationId), headers,
                status);
    }

    /**
     * Handle entity not found.
     *
     * @param ex the ex
     * @return the response entity
     */
    @ExceptionHandler(RequestValidationException.class)
    protected ResponseEntity<Object> handleEntityNotFound(RequestValidationException ex) {
        ex.setStackTrace(new StackTraceElement[0]);
        return response(HttpError.UNPROCESSABLE_ENTITY.getHttpStatus(),
                ex.getCode() != null ? ex.getCode() : HttpError.UNPROCESSABLE_ENTITY.getCode(), ex.getMessage(),
                ex.getMessage());
    }

    /**
     * Unauthorize access.
     *
     * @param ex the ex
     * @return the response entity
     */
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<Object> unauthorizeAccess(AccessDeniedException ex) {
        ex.setStackTrace(new StackTraceElement[0]);
        return response(HttpError.UNAUTHORIZED.getHttpStatus(), HttpError.UNAUTHORIZED.getCode(), ex.getMessage(),
                ex.getMessage());
    }

    /**
     * Invalid OTP access.
     *
     * @param ex the ex
     * @return the response entity
     */
    @ExceptionHandler(InvalidOtpException.class)
    protected ResponseEntity<Object> invalidOtp(InvalidOtpException ex) {
        ex.setStackTrace(new StackTraceElement[0]);
        return response(HttpError.UNAUTHORIZED.getHttpStatus(), HttpError.UNAUTHORIZED.getCode(), ex.getMessage(),
                ex.getMessage());
    }

    /**
     * Two fa key not set exception.
     *
     * @param ex the ex
     * @return the response entity
     */
    @ExceptionHandler(TwoFaKeyNotSetException.class)
    protected ResponseEntity<Object> twoFaKeyNotSetException(TwoFaKeyNotSetException ex) {
        ex.setStackTrace(new StackTraceElement[0]);
        return response(HttpError.UNAUTHORIZED.getHttpStatus(), HttpError.UNAUTHORIZED.getCode(), ex.getMessage(),
                ex.getMessage());
    }
}
