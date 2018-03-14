package org.openkilda.northbound.utils;

/**
 * Correlation id for every single request request.
 */
public final class RequestCorrelation {

    public static final String CORRELATION_ID = "correlation_id";

    private static final ThreadLocal<String> ID = new ThreadLocal<>();

    public static String getId() { return ID.get(); }

    public static void setId(String correlationId) { ID.set(correlationId); }

}
