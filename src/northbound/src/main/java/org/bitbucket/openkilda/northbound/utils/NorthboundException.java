package org.bitbucket.openkilda.northbound.utils;

/**
 * The exception for notifying errors related to missing or invalid entities.
 */
public class NorthboundException extends RuntimeException {
    /**
     * The entity exception type enum.
     */
    public enum EntityExceptionTypes {
        /**
         * The error message for entity not found exception.
         */
        INTERNAL_ERROR("Internal server error"),

        /**
         * The error message for entity not found exception.
         */
        ENTITY_NOT_FOUND("Object was not found"),

        /**
         * The error message for entity already exists exception.
         */
        ENTITY_ALREADY_EXISTS("Object already exists"),

        /**
         * The error message for invalid entity exception.
         */
        ENTITY_INVALID("Invalid object"),

        /**
         * The error message for invalid request exception.
         */
        REQUEST_INVALID("Invalid request"),

        /**
         * The error message for expired token request exception.
         */
        TOKEN_EXPIRED("Token expired"),

        /**
         * The error message for invalid request credentials.
         */
        AUTH_FAILED("Invalid credentials");

        /**
         * The text value.
         */
        private final String text;

        /**
         * Constructs instance by value.
         *
         * @param text the type value
         */
        EntityExceptionTypes(final String text) {
            this.text = text;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The error type.
     */
    private EntityExceptionTypes errorType;

    /**
     * Gets error type.
     *
     * @return the error type
     */
    public EntityExceptionTypes getErrorType() {
        return errorType;
    }

    /**
     * Sets the error type.
     *
     * @param errorType the error type
     */
    public void setErrorType(EntityExceptionTypes errorType) {
        this.errorType = errorType;
    }

    /**
     * Constructs exception.
     *
     * @param errorType the error type
     */
    public NorthboundException(EntityExceptionTypes errorType) {
        super(errorType.toString());
        this.errorType = errorType;
    }

    /**
     * Constructs exception.
     *
     * @param field     the field name
     * @param errorType the error type
     */
    public NorthboundException(String field, EntityExceptionTypes errorType) {
        super(String.format("%s: %s", errorType.toString(), String.valueOf(field)));
        setErrorType(errorType);
    }
}
