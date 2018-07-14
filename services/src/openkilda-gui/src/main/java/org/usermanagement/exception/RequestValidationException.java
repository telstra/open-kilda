package org.usermanagement.exception;

public class RequestValidationException extends CustomException {
    private static final long serialVersionUID = -896015072208863L;

    public RequestValidationException() {}

    public RequestValidationException(String message) {
        super(message);
    }

    public RequestValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestValidationException(int code) {
        super(code);
    }

    public RequestValidationException(int code, String message) {
        super(code, message);
    }

    public RequestValidationException(int code, Throwable cause) {
        super(code, cause);
    }

    public RequestValidationException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public RequestValidationException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(code, message, cause, enableSuppression, writableStackTrace);
    }
}
