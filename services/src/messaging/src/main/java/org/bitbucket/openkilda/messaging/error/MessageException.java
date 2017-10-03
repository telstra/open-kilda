package org.bitbucket.openkilda.messaging.error;


/**
 * The exception for notifying errors.
 */
public class MessageException extends CacheException {
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
     * Instance constructor.
     *
     * @param correlationId    error correlation id
     * @param timestamp        error timestamp
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     */
    public MessageException(String correlationId, long timestamp, ErrorType errorType,
                            String errorMessage, String errorDescription) {
        super(errorType, errorMessage, errorDescription);
        this.correlationId = correlationId;
        this.timestamp = timestamp;
    }

    /**
     * Instance constructor.
     *
     * @param message the {@link MessageError}
     */
    public MessageException(ErrorMessage message) {
        super(message.getData().getErrorType(),
                message.getData().getErrorMessage(),
                message.getData().getErrorDescription());
        this.correlationId = message.getCorrelationId();
        this.timestamp = message.getTimestamp();
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
}
