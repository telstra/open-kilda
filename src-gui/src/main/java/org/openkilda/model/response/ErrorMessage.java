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

package org.openkilda.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class ErrorMessage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"error-code", "error-message", "error-auxiliary-message", "correlationid"})
public class ErrorMessage {

    
    @JsonProperty("error-code")
    private Integer code;

    
    @JsonProperty("error-message")
    private String message;

    
    @JsonProperty("error-auxiliary-message")
    private String auxilaryMessage;

    
    @JsonProperty("correlationid")
    private String correlationId;

    /**
     * Instantiates a new error message.
     */
    public ErrorMessage() {
        super();
    }

    /**
     * Instantiates a new error message.
     *
     * @param code the code
     * @param message the message
     * @param auxilaryMessage the auxilary message
     */
    public ErrorMessage(int code, String message, String auxilaryMessage) {
        super();
        this.code = code;
        this.message = message;
        this.auxilaryMessage = auxilaryMessage;
    }

    /**
     * Instantiates a new error message.
     *
     * @param code the code
     * @param message the message
     * @param auxilaryMessage the auxilary message
     * @param correlationId the correlation id
     */
    public ErrorMessage(Integer code, String message, String auxilaryMessage, String correlationId) {
        super();
        this.code = code;
        this.message = message;
        this.auxilaryMessage = auxilaryMessage;
        this.correlationId = correlationId;
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
     * Sets the code.
     *
     * @param code the new code
     */
    public void setCode(Integer code) {
        this.code = code;
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
     * Sets the message.
     *
     * @param message the new message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the auxilary message.
     *
     * @return the auxilary message
     */
    public String getAuxilaryMessage() {
        return auxilaryMessage;
    }

    /**
     * Sets the auxilary message.
     *
     * @param auxilaryMessage the new auxilary message
     */
    public void setAuxilaryMessage(String auxilaryMessage) {
        this.auxilaryMessage = auxilaryMessage;
    }

    /**
     * Gets the correlation id.
     *
     * @return the correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation id.
     *
     * @param correlationId the new correlation id
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }



}
