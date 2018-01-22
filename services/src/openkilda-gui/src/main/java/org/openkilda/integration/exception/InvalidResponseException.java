package org.openkilda.integration.exception;

public class InvalidResponseException extends RuntimeException {

    private static final long serialVersionUID = 6981852888050194806L;

    private int code;
    private String response;

    public InvalidResponseException() {
        super();
    }

    public InvalidResponseException(final int code, final String response) {
        super();
        this.code = code;
        this.response = response;
    }

    public InvalidResponseException(final int code, final String response, final String errorMessage) {
        super(errorMessage);
        this.code = code;
        this.response = response;
    }

    public InvalidResponseException(final int code, final String response, final String errorMessage,
            final Throwable e) {
        super(errorMessage, e);
        this.code = code;
        this.response = response;
    }

    public int getCode() {
        return code;
    }

    public String getResponse() {
        return response;
    }
}
