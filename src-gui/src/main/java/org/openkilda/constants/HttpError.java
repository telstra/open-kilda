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

package org.openkilda.constants;

import org.openkilda.utility.MessageUtil;

import org.springframework.http.HttpStatus;

/**
 * The Enum HttpError.
 */

public enum HttpError {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, Integer.parseInt(MessageUtil.getCode("0401")),
            MessageUtil.getAuxilaryMessage("0401"), MessageUtil
                    .getMessage("0401")),
    FORBIDDEN(HttpStatus.FORBIDDEN, Integer.parseInt(MessageUtil.getCode("0403")),
            MessageUtil.getAuxilaryMessage("0403"), MessageUtil
                    .getMessage("0403")),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, Integer.parseInt(MessageUtil
            .getCode("0405")), MessageUtil.getAuxilaryMessage("0405"),
            MessageUtil.getMessage("0405")),
    METHOD_NOT_FOUND(HttpStatus.NOT_FOUND,
            Integer.parseInt(MessageUtil.getCode("0404")), MessageUtil
                    .getAuxilaryMessage("0404"), MessageUtil.getMessage("0404")),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Integer.parseInt(MessageUtil
            .getCode("0500")), MessageUtil.getAuxilaryMessage("0500"),
            MessageUtil.getMessage("0500")),
    GATEWAY_TIMEOUT_ERROR(HttpStatus.GATEWAY_TIMEOUT, Integer.parseInt(MessageUtil
            .getCode("0504")), MessageUtil.getAuxilaryMessage("0504"),
            MessageUtil.getMessage("0504")),
    BAD_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, Integer.parseInt(MessageUtil
            .getCode("0502")), MessageUtil.getAuxilaryMessage("0502"),
            MessageUtil.getMessage("0502")),
    PAYLOAD_NOT_VALID_JSON(HttpStatus.BAD_REQUEST, Integer.parseInt(MessageUtil
            .getCode("0406")), MessageUtil.getAuxilaryMessage("0406"),
            MessageUtil.getMessage("0406")),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, Integer.parseInt(MessageUtil.getCode("0400")),
            MessageUtil.getAuxilaryMessage("0400"), MessageUtil
                    .getMessage("0400")),
    OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND,
            Integer.parseInt(MessageUtil.getCode("0002")), MessageUtil
                    .getAuxilaryMessage("0002"), MessageUtil.getMessage("0002")),
    STATUS_CONFLICT(HttpStatus.CONFLICT, Integer.parseInt(MessageUtil.getCode("0001")),
            MessageUtil.getAuxilaryMessage("0001"), MessageUtil
                    .getMessage("0001")),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, Integer.parseInt(MessageUtil
            .getCode("0003")), MessageUtil.getAuxilaryMessage("0003"),
            MessageUtil.getMessage("0003")),
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, Integer.parseInt(MessageUtil
            .getCode("0412")), MessageUtil.getAuxilaryMessage("0412"),
            MessageUtil.getMessage("0412")),
    RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, Integer.parseInt(MessageUtil
            .getCode("0004")), MessageUtil.getAuxilaryMessage("0004"),
            MessageUtil.getMessage("0004")),
    NO_CONTENT(HttpStatus.NO_CONTENT, Integer.parseInt(MessageUtil
            .getCode("0204")), MessageUtil.getAuxilaryMessage("0204"),
            MessageUtil.getMessage("0204")),
    STORE_INTEGRATION_ERROR(HttpStatus.EXPECTATION_FAILED, Integer.parseInt(MessageUtil
            .getCode("0417")), MessageUtil.getAuxilaryMessage("0417"),
            MessageUtil.getMessage("0417"));

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
    private HttpError(final HttpStatus httpStatus, final Integer code, final String auxilaryMessage,
            final String message) {
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
