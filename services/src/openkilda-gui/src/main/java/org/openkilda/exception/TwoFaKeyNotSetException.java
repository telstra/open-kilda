package org.openkilda.exception;

import org.usermanagement.exception.CustomException;

/**
 * The Class UnauthorizedException.
 */
public class TwoFaKeyNotSetException extends CustomException {

    private static final long serialVersionUID = 4768494178056774577L;

    /**
     * Instantiates when user is unauthorized.
     *
     * @param message the message
     */
    public TwoFaKeyNotSetException() {
        super();
    }
    
    public TwoFaKeyNotSetException(final String message) {
        super(message);
    }

    public TwoFaKeyNotSetException(String message, Throwable cause) {
        super(message, cause);
    }

    public TwoFaKeyNotSetException(int code) {
        super(code);
    }

    public TwoFaKeyNotSetException(int code, String message) {
        super(code, message);
    }

    public TwoFaKeyNotSetException(int code, Throwable cause) {
        super(code, cause);
    }

    public TwoFaKeyNotSetException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public TwoFaKeyNotSetException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(code, message, cause, enableSuppression, writableStackTrace);
    }
}
