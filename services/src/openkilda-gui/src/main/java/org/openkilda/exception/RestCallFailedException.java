package org.openkilda.exception;

/**
 * The Class RestCallFailedException.
 */
public class RestCallFailedException extends RuntimeException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new rest call failed exception.
     */
    public RestCallFailedException() {
        super();
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param message the message
     * @param cause the cause
     * @param enableSuppression the enable suppression
     * @param writableStackTrace the writable stack trace
     */
    public RestCallFailedException(final String message, final Throwable cause,
            final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public RestCallFailedException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param message the message
     */
    public RestCallFailedException(final String message) {
        super(message);
    }

    /**
     * Instantiates a new rest call failed exception.
     *
     * @param cause the cause
     */
    public RestCallFailedException(final Throwable cause) {
        super(cause);
    }
}
