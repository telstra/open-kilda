/* Copyright 2024 Telstra Open Source
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

import static org.openkilda.messaging.Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS;
import static org.openkilda.messaging.Utils.HEALTH_CHECK_OPERATIONAL_STATUS;

import org.openkilda.bluegreen.LifeCycleObserver;
import org.openkilda.bluegreen.Signal;
import org.openkilda.bluegreen.ZkWatchDog;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.northbound.config.MessageProducerConfig;
import org.openkilda.northbound.service.HealthCheckService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages health-check operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckImpl implements HealthCheckService, LifeCycleObserver {
    private static final String KAFKA_STATUS_KEY = "kafka";
    private static final String ZOOKEEPER_STATUS_KEY = "zookeeper";
    private static final String ZK_COMPONENT_NAME = "northbound";

    private final BuildProperties buildProperties;
    private final MessageProducerConfig messageProducerConfig;

    /**
     * The service name.
     */
    private String serviceName;

    /**
     * The service version.
     */
    private String serviceVersion;

    /**
     * The service description.
     */
    private String serviceDescription;

    /**
     * Interal status.
     */
    private Map<String, String> status = new ConcurrentHashMap<>();

    private ZkWatchDog watchDog;

    /**
     * Initialization.
     */
    @PostConstruct
    public void init() {
        this.serviceName = buildProperties.getName();
        this.serviceVersion = buildProperties.getVersion();
        this.serviceDescription = String.format("%s.%s", buildProperties.getArtifact(), buildProperties.getGroup());

        if (StringUtils.hasText(messageProducerConfig.getZookeeperConnectString())) {
            watchDog = ZkWatchDog.builder().id(messageProducerConfig.getBlueGreenMode())
                    .serviceName(ZK_COMPONENT_NAME)
                    .reconnectDelayMs(messageProducerConfig.getZookeeperReconnectDelayMs())
                    .connectionString(messageProducerConfig.getZookeeperConnectString()).build();
            watchDog.subscribe(this);
            watchDog.initAndWaitConnection();
        }
    }

    /**
     * The health-check info bean.
     *
     * @return the FlowModel instance
     */
    @Override
    public HealthCheck getHealthCheck() {
        log.info("API request: Get health check");
        Map<String, String> currentStatus = new HashMap<>();
        currentStatus.put(KAFKA_STATUS_KEY, Utils.HEALTH_CHECK_OPERATIONAL_STATUS);
        checkZookeeperStatus();
        currentStatus.putAll(status);
        return new HealthCheck(serviceName, serviceVersion, serviceDescription, currentStatus);
    }

    /**
     * The health-check info bean.
     *
     */
    @Override
    public void updateKafkaStatus(String status) {
        log.info("API request: Update Kafka status {}", status);
        this.status.put(KAFKA_STATUS_KEY, status);
    }

    @Override
    public void updateZookeeperStatus(String status) {
        log.info("API request: Update Zookeeper status {}", status);
        this.status.put(ZOOKEEPER_STATUS_KEY, status);
    }

    @Override
    public void handle(Signal signal) {
        log.info("Component {} with id {} received signal {}", "ZK_COMPONENT_NAME", "region", signal);
    }

    private void checkZookeeperStatus() {
        try {
            watchDog.getVersionSync();
            updateZookeeperStatus(HEALTH_CHECK_OPERATIONAL_STATUS);
        } catch (Exception e) {
            updateZookeeperStatus(HEALTH_CHECK_NON_OPERATIONAL_STATUS);
            log.error(String.format("Component %s with id %s caught exception during getting messaging version",
                    ZK_COMPONENT_NAME, messageProducerConfig.getBlueGreenMode()), e);
        }
    }
}
