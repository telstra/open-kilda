package org.openkilda.exception;

/**
 * The Class UnauthorizedException.
 */
public class UnauthorizedException extends RuntimeException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -6680840187838156716L;

    /**
     * Instantiates when user is unauthorized.
     *
     * @param message the message
     */
    public UnauthorizedException(final String message) {
        super(message);

    }

}
