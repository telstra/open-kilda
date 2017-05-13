package org.bitbucket.openkilda.northbound.utils;

import org.bitbucket.openkilda.messaging.error.ErrorType;

/**
 * The exception for notifying errors.
 */
public class NorthboundException extends RuntimeException {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The error type.
     */
    private ErrorType errorType;

    /**
     * The timestamp.
     */
    private long timestamp;

    /**
     * Gets error type.
     *
     * @return the error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Sets the error type.
     *
     * @param errorType the error type
     */
    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    /**
     * Gets error timestamp.
     *
     * @return the error timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the error timestamp.
     *
     * @param timestamp the error timestamp
     */
    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Constructs exception.
     *
     * @param errorType the error type
     * @param timestamp the error timestamp
     */
    public NorthboundException(final ErrorType errorType, final long timestamp) {
        super(errorType.toString());
        this.errorType = errorType;
        this.timestamp = timestamp;
    }
}
