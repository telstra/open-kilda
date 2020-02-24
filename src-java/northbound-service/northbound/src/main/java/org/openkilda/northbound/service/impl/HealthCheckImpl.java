/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.northbound.service.impl;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.northbound.service.HealthCheckService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages health-check operation.
 */
@Service
public class HealthCheckImpl implements HealthCheckService {
    private static final String KAFKA_STATUS_KEY = "kafka";
    /**
     * The service name.
     */
    @Value("${service.name}")
    private String serviceName;

    /**
     * The service version.
     */
    @Value("${service.version}")
    private String serviceVersion;

    /**
     * The service description.
     */
    @Value("${service.description}")
    private String serviceDescription;

    /**
     * Interal status.
     */
    private Map<String, String> status = new ConcurrentHashMap<>();

    /**
     * The health-check info bean.
     *
     * @return the FlowModel instance
     */
    @Override
    public HealthCheck getHealthCheck() {
        Map<String, String> currentStatus = new HashMap<>();
        currentStatus.put(KAFKA_STATUS_KEY, Utils.HEALTH_CHECK_OPERATIONAL_STATUS);
        currentStatus.putAll(status);
        return new HealthCheck(serviceName, serviceVersion, serviceDescription, currentStatus);
    }

    /**
     * The health-check info bean.
     *
     */
    @Override
    public void updateKafkaStatus(String status) {
        this.status.put(KAFKA_STATUS_KEY, status);
    }
}
