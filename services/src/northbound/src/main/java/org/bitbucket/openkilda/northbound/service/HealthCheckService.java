package org.bitbucket.openkilda.northbound.service;

import org.bitbucket.openkilda.messaging.model.HealthCheck;

/**
 * HealthCheckService is for getting info about components status.
 */
public interface HealthCheckService {
    /**
     * Gets health-check status.
     *
     * @param correlationId request correlation id
     * @return {@link HealthCheckService} instance
     */
    HealthCheck getHealthCheck(String correlationId);
}
