package org.openkilda.integration.exception;

public class ContentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 9177586156625415602L;

    public ContentNotFoundException() {
        super();
    }

    public ContentNotFoundException(final String errorMessage) {
        super(errorMessage);
    }

    public ContentNotFoundException(final String errorMessage, final Throwable e) {
        super(errorMessage, e);
    }

    public ContentNotFoundException(final Throwable e) {
        super(e);
    }
}
