package org.openkilda.constants;

import org.springframework.http.HttpStatus;

import org.openkilda.service.MessagePropertyService;

/**
 * The Enum HttpError.
 */
public enum HttpError {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, Integer.parseInt(MessagePropertyService.getCode("0401")),
            MessagePropertyService.getAuxilaryMessage("0401"), MessagePropertyService
                    .getMessage("0401")),
    FORBIDDEN(HttpStatus.FORBIDDEN, Integer.parseInt(MessagePropertyService.getCode("0403")),
            MessagePropertyService.getAuxilaryMessage("0403"), MessagePropertyService
                    .getMessage("0403")),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, Integer.parseInt(MessagePropertyService
            .getCode("0405")), MessagePropertyService.getAuxilaryMessage("0405"),
            MessagePropertyService.getMessage("0405")),
    METHOD_NOT_FOUND(HttpStatus.NOT_FOUND,
            Integer.parseInt(MessagePropertyService.getCode("0404")), MessagePropertyService
                    .getAuxilaryMessage("0404"), MessagePropertyService.getMessage("0404")),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Integer.parseInt(MessagePropertyService
            .getCode("0500")), MessagePropertyService.getAuxilaryMessage("0500"),
            MessagePropertyService.getMessage("0500")),
    GATEWAY_TIMEOUT_ERROR(HttpStatus.GATEWAY_TIMEOUT, Integer.parseInt(MessagePropertyService
            .getCode("0504")), MessagePropertyService.getAuxilaryMessage("0504"),
            MessagePropertyService.getMessage("0504")),
    BAD_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, Integer.parseInt(MessagePropertyService
            .getCode("0502")), MessagePropertyService.getAuxilaryMessage("0502"),
            MessagePropertyService.getMessage("0502")),
    PAYLOAD_NOT_VALID_JSON(HttpStatus.BAD_REQUEST, Integer.parseInt(MessagePropertyService
            .getCode("0406")), MessagePropertyService.getAuxilaryMessage("0406"),
            MessagePropertyService.getMessage("0406")),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, Integer.parseInt(MessagePropertyService.getCode("0400")),
            MessagePropertyService.getAuxilaryMessage("0400"), MessagePropertyService
                    .getMessage("0400")),
    OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND,
            Integer.parseInt(MessagePropertyService.getCode("0002")), MessagePropertyService
                    .getAuxilaryMessage("0002"), MessagePropertyService.getMessage("0002")),
    STATUS_CONFLICT(HttpStatus.CONFLICT, Integer.parseInt(MessagePropertyService.getCode("0001")),
            MessagePropertyService.getAuxilaryMessage("0001"), MessagePropertyService
                    .getMessage("0001")),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, Integer.parseInt(MessagePropertyService
            .getCode("0003")), MessagePropertyService.getAuxilaryMessage("0003"),
            MessagePropertyService.getMessage("0003")),
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, Integer.parseInt(MessagePropertyService
            .getCode("0412")), MessagePropertyService.getAuxilaryMessage("0412"),
            MessagePropertyService.getMessage("0412")),
    RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, Integer.parseInt(MessagePropertyService
            .getCode("0004")), MessagePropertyService.getAuxilaryMessage("0004"),
            MessagePropertyService.getMessage("0004"));

    private HttpStatus httpStatus;
    private Integer code;
    private String message;
    private String auxilaryMessage;

    /**
     * Instantiates a new http error.
     *
     * @param httpStatus the http status
     * @param code the code
     * @param auxilaryMessage the auxilary message
     * @param message the message
     */
    private HttpError(final HttpStatus httpStatus, final Integer code, final String auxilaryMessage, final String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.auxilaryMessage = auxilaryMessage;
        this.message = message;
    }

    /**
     * Gets the http status.
     *
     * @return the http status
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Gets the code.
     *
     * @return the code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * Gets the message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the auxilary message.
     *
     * @return the auxilary message
     */
    public String getAuxilaryMessage() {
        return auxilaryMessage;
    }

}
