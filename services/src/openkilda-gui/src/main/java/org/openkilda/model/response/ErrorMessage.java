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

    /** The code. */
    @JsonProperty("error-code")
    private Integer code;

    /** The message. */
    @JsonProperty("error-message")
    private String message;

    /** The auxilary message. */
    @JsonProperty("error-auxiliary-message")
    private String auxilaryMessage;

    /** The correlation id. */
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
