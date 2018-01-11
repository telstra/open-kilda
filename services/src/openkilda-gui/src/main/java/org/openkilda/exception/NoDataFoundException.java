package org.openkilda.exception;

/**
 * The Class UnauthorizedException.
 */
public class NoDataFoundException extends RuntimeException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -6680840187838156716L;

    /**
     * Instantiates when user is unauthorized.
     *
     * @param message the message
     */
    public NoDataFoundException(final String message) {
        super(message);

    }

}
