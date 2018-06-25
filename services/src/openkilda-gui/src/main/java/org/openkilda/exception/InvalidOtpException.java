package org.openkilda.exception;

import org.usermanagement.exception.CustomException;

/**
 * The Class UnauthorizedException.
 */
public class InvalidOtpException extends CustomException {

    private static final long serialVersionUID = 4768494178056774577L;

    /**
     * Instantiates when user is unauthorized.
     *
     * @param message the message
     */
    public InvalidOtpException(final String message) {
        super(message);
    }

    public InvalidOtpException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidOtpException(int code) {
        super(code);
    }

    public InvalidOtpException(int code, String message) {
        super(code, message);
    }

    public InvalidOtpException(int code, Throwable cause) {
        super(code, cause);
    }

    public InvalidOtpException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public InvalidOtpException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(code, message, cause, enableSuppression, writableStackTrace);
    }
}
