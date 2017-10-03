package org.bitbucket.openkilda.messaging.error;


/**
 * The exception for notifying errors.
 */
public class CacheException extends RuntimeException {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

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
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     */
    public CacheException(ErrorType errorType, String errorMessage, String errorDescription) {
        super(errorMessage);
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.errorDescription = errorDescription;
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
