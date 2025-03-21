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
import org.openkilda.exception.NoDataFoundException;
import org.openkilda.integration.exception.ContentNotFoundException;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.exception.StoreIntegrationException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;

/**
 * The Class CustomExceptionMapper.
 */
@Slf4j
@ControllerAdvice
public class CustomExceptionMapper extends GlobalExceptionMapper {

    /**
     * Instantiates a new custom exception mapper.
     */
    public CustomExceptionMapper() {
        log.info("Custom exception mapper. Initializing {}...", CustomExceptionMapper.class.getName());
    }

    /**
     * Default exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {Exception.class})
    protected ResponseEntity<Object> defaultExceptionHandler(final Exception ex, final WebRequest request) {
        return response(HttpError.INTERNAL_ERROR.getHttpStatus(), HttpError.INTERNAL_ERROR.getCode(),
                HttpError.INTERNAL_ERROR.getAuxilaryMessage(), HttpError.INTERNAL_ERROR.getMessage(), "");
    }

    /**
     * Constraint violation exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {ConstraintViolationException.class})
    protected ResponseEntity<Object> constraintViolationExceptionHandler(final ConstraintViolationException ex,
                                                                         final WebRequest request) {
        String message = ex.getConstraintViolations().stream().map(ConstraintViolation::getMessage)
                .sorted(Collections.reverseOrder()).collect(joining(", "));
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), message);
    }

    /**
     * Integration exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {IntegrationException.class})
    protected ResponseEntity<Object> integrationExceptionHandler(final IntegrationException ex,
                                                                 final WebRequest request) {
        return response(HttpError.INTERNAL_ERROR.getHttpStatus(), HttpError.INTERNAL_ERROR.getCode(),
                HttpError.INTERNAL_ERROR.getAuxilaryMessage(), ex.toString());
    }

    /**
     * StoreIntegrationException exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {StoreIntegrationException.class})
    protected ResponseEntity<Object> storeIntegrationExceptionHandler(final StoreIntegrationException ex,
                                                                      final WebRequest request) {
        return response(HttpError.STORE_INTEGRATION_ERROR.getHttpStatus(), HttpError.STORE_INTEGRATION_ERROR.getCode(),
                HttpError.STORE_INTEGRATION_ERROR.getAuxilaryMessage(), HttpError.STORE_INTEGRATION_ERROR.getMessage());
    }

    /**
     * Invalid response exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {InvalidResponseException.class, NoDataFoundException.class})
    protected ResponseEntity<Object> invalidResponseExceptionHandler(final InvalidResponseException ex,
                                                                     final WebRequest request) {
        if (ex.getResponse() != null) {
            JSONParser jsonParser = new JSONParser();
            try {
                JSONObject jsonObject = (JSONObject) jsonParser.parse(ex.getResponse());
                String errorMessage = HttpError.PRECONDITION_FAILED.getAuxilaryMessage();
                String errorType = ex.toString();
                String errorDescription = ex.toString();
                if (jsonObject.get("error-message") != null) {
                    errorMessage = jsonObject.get("error-message").toString();
                }
                if (jsonObject.get("error-type") != null) {
                    errorType = jsonObject.get("error-type").toString();
                }
                if (jsonObject.get("error-description") != null) {
                    errorDescription = jsonObject.get("error-description").toString();
                }

                return response(HttpError.PRECONDITION_FAILED.getHttpStatus(), HttpError.PRECONDITION_FAILED.getCode(),
                        errorMessage, errorType, "", errorDescription);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return response(HttpError.PRECONDITION_FAILED.getHttpStatus(), HttpError.PRECONDITION_FAILED.getCode(),
                HttpError.PRECONDITION_FAILED.getAuxilaryMessage(), ex.toString());
    }

    /**
     * Content not found exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {ContentNotFoundException.class})
    protected ResponseEntity<Object> contentNotFoundExceptionHandler(final ContentNotFoundException ex,
                                                                     final WebRequest request) {
        return response(HttpStatus.NO_CONTENT, HttpError.NO_CONTENT.getCode(),
                HttpError.NO_CONTENT.getAuxilaryMessage(), ex.toString());
    }

    /**
     * Method argument type mismatch exception handler.
     *
     * @param ex      the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {MethodArgumentTypeMismatchException.class})
    protected ResponseEntity<Object> methodArgumentTypeMismatchExceptionHandler(
            final MethodArgumentTypeMismatchException ex, final WebRequest request) {
        String errorMessage = "Invalid method argument type. Required Type: " + ex.getRequiredType();
        return response(HttpStatus.BAD_REQUEST, HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), errorMessage);
    }
}
