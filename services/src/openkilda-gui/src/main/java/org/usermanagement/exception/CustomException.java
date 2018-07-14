package org.usermanagement.exception;

public abstract class CustomException extends RuntimeException {
    private static final long serialVersionUID = 6970024132461386518L;
    private Integer code;

    public CustomException() {}

    public CustomException(String message) {
        super(message);
    }

    public CustomException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomException(int code) {
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, String message) {
        super(message);
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, Throwable cause) {
        super(cause);
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = Integer.valueOf(code);
    }

    public Integer getCode() {
        return this.code;
    }
}
