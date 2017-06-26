package org.bitbucket.openkilda.messaging.error;


/**
 * The exception for notifying errors.
 */
public class MessageException extends RuntimeException {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The correlation id.
     */
    protected String correlationId;

    /**
     * The timestamp.
     */
    protected long timestamp;

    /**
     * The error type.
     */
    protected ErrorType errorType;

    /**
     * The error message.
     */
    protected String errorMessage;

    /**
     * The error description.
     */
    protected String errorDescription;

    /**
     * Instance constructor.
     *
     * @param correlationId    error correlation id
     * @param timestamp        error timestamp
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     */
    public MessageException(final String correlationId, final long timestamp, final ErrorType errorType,
                            final String errorMessage, final String errorDescription) {
        super(errorMessage);
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.errorDescription = errorDescription;
    }

    /**
     * Instance constructor.
     *
     * @param message the {@link MessageError}
     */
    public MessageException(final ErrorMessage message) {
        super(message.getData().getErrorMessage());
        this.correlationId = message.getCorrelationId();
        this.timestamp = message.getTimestamp();
        this.errorType = message.getData().getErrorType();
        this.errorMessage = message.getData().getErrorMessage();
        this.errorDescription = message.getData().getErrorDescription();
    }

    /**
     * Returns error correlation id.
     *
     * @return error correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns error timestamp.
     *
     * @return error timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns error type.
     *
     * @return error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns error message.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns error description.
     *
     * @return error description
     */
    public String getErrorDescription() {
        return errorDescription;
    }
}
