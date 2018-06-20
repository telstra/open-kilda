package org.openkilda.exception;

import org.usermanagement.exception.CustomException;

/**
 * The Class UnauthorizedException.
 */
public class OtpRequiredException extends CustomException {

    private static final long serialVersionUID = 4768494178056774577L;

    /**
     * Instantiates when user is unauthorized.
     *
     * @param message the message
     */
    public OtpRequiredException() {
        super();
    }

    public OtpRequiredException(final String message) {
        super(message);
    }

    public OtpRequiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public OtpRequiredException(int code) {
        super(code);
    }

    public OtpRequiredException(int code, String message) {
        super(code, message);
    }

    public OtpRequiredException(int code, Throwable cause) {
        super(code, cause);
    }

    public OtpRequiredException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public OtpRequiredException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(code, message, cause, enableSuppression, writableStackTrace);
    }
}
