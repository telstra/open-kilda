package org.openkilda.exception;

/**
 * The Class ExternalSystemException.
 */
public class ExternalSystemException extends RuntimeException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The code. */
    private Integer code = null;

    /**
     * Instantiates a new external system exception.
     */
    public ExternalSystemException() {
        super();
    }

    /**
     * Instantiates a new external system exception.
     *
     * @param code the code
     * @param message the message
     * @param cause the cause
     * @param enableSuppression the enable suppression
     * @param writableStackTrace the writable stack trace
     */
    public ExternalSystemException(final Integer code, final String message, final Throwable cause,
            final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = code;
    }

    /**
     * Instantiates a new external system exception.
     *
     * @param code the code
     * @param message the message
     * @param cause the cause
     */
    public ExternalSystemException(final Integer code, final String message, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Instantiates a new external system exception.
     *
     * @param code the code
     * @param message the message
     */
    public ExternalSystemException(final Integer code, final String message) {
        super(message);
        this.code = code;
    }

    /**
     * Instantiates a new external system exception.
     *
     * @param cause the cause
     */
    public ExternalSystemException(final Throwable cause) {
        super(cause);
    }

    /**
     * Gets the code.
     *
     * @return the code
     */
    public Integer getCode() {
        return code;
    }
}
