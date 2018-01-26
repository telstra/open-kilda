package org.openkilda.integration.exception;

public class IntegrationException extends RuntimeException {

    private static final long serialVersionUID = 9177586156625415602L;

    public IntegrationException() {
        super();
    }

    public IntegrationException(final String errorMessage) {
        super(errorMessage);
    }

    public IntegrationException(final String errorMessage, final Throwable e) {
        super(errorMessage, e);
    }

    public IntegrationException(final Throwable e) {
        super(e);
    }
}
