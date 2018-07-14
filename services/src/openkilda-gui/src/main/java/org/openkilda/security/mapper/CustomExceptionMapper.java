package org.openkilda.security.mapper;

import static java.util.stream.Collectors.joining;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Collections;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openkilda.constants.HttpError;
import org.openkilda.integration.exception.ContentNotFoundException;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;

/**
 * The Class CustomExceptionMapper.
 */
@ControllerAdvice
public class CustomExceptionMapper extends GlobalExceptionMapper {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(CustomExceptionMapper.class);

    /**
     * Instantiates a new custom exception mapper.
     */
    public CustomExceptionMapper() {
        _log.info("[CustomExceptionMapper] Initializing {}...",
                CustomExceptionMapper.class.getName());
    }

    /**
     * Default exception handler.
     *
     * @param ex the ex
     * @param request the request
     * @return the response entity
     */
    @ExceptionHandler(value = {Exception.class})
    protected ResponseEntity<Object> defaultExceptionHandler(final Exception ex,
            final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.INTERNAL_ERROR.getHttpStatus(),
                HttpError.INTERNAL_ERROR.getCode(), HttpError.INTERNAL_ERROR.getAuxilaryMessage(),
                HttpError.INTERNAL_ERROR.getMessage(), "");
    }

    @ExceptionHandler(value = {ConstraintViolationException.class})
    protected ResponseEntity<Object> constraintViolationExceptionHandler(
            final ConstraintViolationException ex, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        String message = ex.getConstraintViolations().stream().map(ConstraintViolation::getMessage)
                .sorted(Collections.reverseOrder()).collect(joining(", "));
        return response(HttpError.BAD_REQUEST.getHttpStatus(), HttpError.BAD_REQUEST.getCode(),
                HttpError.BAD_REQUEST.getAuxilaryMessage(), message);
    }

    @ExceptionHandler(value = {IntegrationException.class})
    protected ResponseEntity<Object> integrationExceptionHandler(
            final IntegrationException ex, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpError.INTERNAL_ERROR.getHttpStatus(),
                HttpError.INTERNAL_ERROR.getCode(), HttpError.INTERNAL_ERROR.getAuxilaryMessage(),
                ex.toString());
    }

    @ExceptionHandler(value = {InvalidResponseException.class})
    protected ResponseEntity<Object> invalidResponseExceptionHandler(
            final InvalidResponseException ex, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        if (ex.getResponse() != null) {
            JSONParser jsonParser = new JSONParser();
            try {
                JSONObject jsonObject = (JSONObject) jsonParser.parse(ex.getResponse());
                return response(HttpError.PRECONDITION_FAILED.getHttpStatus(),
                        HttpError.PRECONDITION_FAILED.getCode(),
                        jsonObject.get("error-message").toString(),
                        jsonObject.get("error-type").toString());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return response(HttpError.PRECONDITION_FAILED.getHttpStatus(),
                HttpError.PRECONDITION_FAILED.getCode(), HttpError.PRECONDITION_FAILED.getAuxilaryMessage(),
                ex.toString());
    }

    @ExceptionHandler(value = {ContentNotFoundException.class})
    protected ResponseEntity<Object> contentNotFoundExceptionHandler(
            final ContentNotFoundException ex, final WebRequest request) {
        _log.error("Exception: " + ex.getMessage(), ex);
        return response(HttpStatus.NO_CONTENT, HttpError.NO_CONTENT.getCode(),
                HttpError.NO_CONTENT.getAuxilaryMessage(), ex.toString());
    }
}
